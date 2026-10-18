package com.repwise.audio

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.repwise.MainActivity
import com.repwise.R
import com.repwise.RepwiseApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps workout music and ambient sounds playing while Repwise is in the background, with a
 * notification that offers play/pause and stop. Stops itself as soon as playback is stopped.
 */
class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private lateinit var engine: AudioEngine

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Headphones unplugged: pause instead of suddenly playing through the speaker.
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) engine.pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = AudioEngine.get(this)
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = engine.state.value
        // startForeground must be called promptly after startForegroundService, even if we stop right after.
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(state), type)
        when (intent?.action) {
            ACTION_TOGGLE -> engine.togglePlay()
            ACTION_STOP -> engine.stop()
        }
        if (!engine.state.value.hasContent) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (observer == null) {
            observer = scope.launch {
                engine.state.collectLatest { latest ->
                    if (!latest.hasContent) {
                        ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(latest))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(state: PlayerState): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SOUNDS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, RepwiseApplication.CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_stat_repwise)
            .setContentTitle(state.label)
            .setContentText(if (state.playing) "Playing in Repwise" else "Paused")
            .setContentIntent(open)
            .setOngoing(state.playing)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (state.playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (state.playing) "Pause" else "Play",
                serviceIntent(ACTION_TOGGLE, 1),
            )
            .addAction(R.drawable.ic_stop, "Stop", serviceIntent(ACTION_STOP, 2))
            .build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away while paused shouldn't leave a stale notification behind.
        if (!engine.state.value.playing) engine.stop()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 41
        private const val ACTION_TOGGLE = "com.repwise.audio.TOGGLE"
        private const val ACTION_STOP = "com.repwise.audio.STOP"
    }
}
