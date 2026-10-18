package com.repwise.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs

data class PlayerState(
    val track: MusicTrack? = null,
    val musicVolume: Float = 0.7f,
    val layers: Map<AmbientSound, Float> = emptyMap(),
    val playing: Boolean = false,
    val loadingTrack: Boolean = false,
    val error: String? = null,
) {
    val hasContent: Boolean get() = track != null || layers.isNotEmpty()
    val label: String get() = listOfNotNull(track?.title, layers.keys.joinToString(" + ") { it.title }.ifBlank { null })
        .joinToString(" · ")
        .ifBlank { "Nothing selected" }
}

/**
 * Mixes one looping music track with any number of ambient generators into a single
 * streaming [AudioTrack]. The render thread only runs while something is playing, and the
 * track is released when playback stops, so idle cost is zero.
 */
class AudioEngine private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val renderExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "repwise-track-render") }

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    @Volatile private var musicLoop: FloatArray? = null
    private val voices = ConcurrentHashMap<AmbientSound, Voice>()
    private val lock = Object()
    private var renderThread: Thread? = null
    @Volatile private var pausedByFocusLoss = false

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (_state.value.playing) {
                    pausedByFocusLoss = true
                    setPlaying(false)
                }
                AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    setPlaying(true)
                }
            }
        }
        .build()

    fun selectTrack(track: MusicTrack?) {
        if (track == _state.value.track) return
        _state.update { it.copy(track = track, loadingTrack = track != null, error = null) }
        musicLoop = null
        if (track == null) return
        renderExecutor.execute {
            try {
                val rendered = track.render()
                if (_state.value.track == track) {
                    musicLoop = rendered
                    _state.update { it.copy(loadingTrack = false) }
                }
            } catch (error: OutOfMemoryError) {
                Log.e(TAG, "Not enough memory to render ${track.title}", error)
                _state.update { it.copy(track = null, loadingTrack = false, error = "Not enough memory to load ${track.title}.") }
            }
        }
    }

    fun playTrack(track: MusicTrack) {
        selectTrack(track)
        play()
    }

    fun setMusicVolume(volume: Float) = _state.update { it.copy(musicVolume = volume.coerceIn(0f, 1f)) }

    fun toggleLayer(sound: AmbientSound) {
        if (sound in _state.value.layers) {
            voices.remove(sound)
            _state.update { it.copy(layers = it.layers - sound) }
        } else {
            voices[sound] = sound.newVoice()
            _state.update { it.copy(layers = it.layers + (sound to 0.6f)) }
            if (!_state.value.playing) play()
        }
    }

    fun setLayerVolume(sound: AmbientSound, volume: Float) =
        _state.update { if (sound in it.layers) it.copy(layers = it.layers + (sound to volume.coerceIn(0f, 1f))) else it }

    fun applyMix(mix: SavedMix) {
        voices.clear()
        mix.layers.keys.forEach { voices[it] = it.newVoice() }
        _state.update { it.copy(layers = mix.layers, musicVolume = mix.musicVolume) }
        selectTrack(mix.track)
        play()
    }

    fun togglePlay() = if (_state.value.playing) pause() else play()

    fun play() {
        if (!_state.value.hasContent) return
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) {
            _state.update { it.copy(error = "Another app is using audio right now.") }
            return
        }
        pausedByFocusLoss = false
        setPlaying(true)
        try {
            ContextCompat.startForegroundService(appContext, Intent(appContext, PlaybackService::class.java))
        } catch (error: IllegalStateException) {
            // Can't start a foreground service from the background; playback continues while the app is visible.
            Log.w(TAG, "Background playback service unavailable", error)
        }
    }

    fun pause() {
        pausedByFocusLoss = false
        setPlaying(false)
    }

    fun stop() {
        pause()
        voices.clear()
        musicLoop = null
        _state.value = PlayerState(musicVolume = _state.value.musicVolume)
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun setPlaying(playing: Boolean) {
        _state.update { it.copy(playing = playing, error = null) }
        synchronized(lock) {
            if (playing && renderThread?.isAlive != true) {
                renderThread = Thread(::renderLoop, "repwise-mixer").also { it.start() }
            }
            lock.notifyAll()
        }
    }

    private fun renderLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBuffer, CHUNK * 4))
                .build()
        } catch (error: Exception) {
            Log.e(TAG, "Audio output unavailable", error)
            _state.update { it.copy(playing = false, error = "Audio output is unavailable on this device.") }
            return
        }
        val pcm = ShortArray(CHUNK)
        var musicIndex = 0
        var musicGain = 0f
        val layerGain = HashMap<AmbientSound, Float>()
        try {
            track.play()
            while (true) {
                val snapshot = _state.value
                if (!snapshot.playing) break
                val loop = musicLoop
                val musicTarget = if (loop != null) snapshot.musicVolume else 0f
                // Snapshot layers once per chunk so the per-sample loop allocates nothing.
                val active = voices.entries.toList()
                val sounds = Array(active.size) { active[it].key }
                val generators = Array(active.size) { active[it].value }
                val targets = FloatArray(active.size) { snapshot.layers[sounds[it]] ?: 0f }
                val gains = FloatArray(active.size) { layerGain[sounds[it]] ?: 0f }
                for (i in 0 until CHUNK) {
                    musicGain += (musicTarget - musicGain) * SMOOTHING
                    var sample = 0f
                    if (loop != null) {
                        if (musicIndex >= loop.size) musicIndex = 0
                        sample += loop[musicIndex++] * musicGain
                    }
                    for (v in generators.indices) {
                        gains[v] += (targets[v] - gains[v]) * SMOOTHING
                        sample += generators[v].next() * gains[v]
                    }
                    pcm[i] = (softClip(sample) * Short.MAX_VALUE).toInt().toShort()
                }
                layerGain.clear()
                sounds.forEachIndexed { v, sound -> layerGain[sound] = gains[v] }
                if (track.write(pcm, 0, CHUNK) < 0) {
                    _state.update { it.copy(playing = false, error = "Audio playback was interrupted.") }
                    break
                }
            }
        } finally {
            track.pause()
            track.flush()
            track.release()
            synchronized(lock) {
                renderThread = null
                // Play may have been pressed again while this thread was shutting down.
                if (_state.value.playing) renderThread = Thread(::renderLoop, "repwise-mixer").also { it.start() }
            }
        }
    }

    private fun softClip(x: Float): Float = if (abs(x) < 0.6f) x else (x / (1 + abs(x) - 0.6f)).coerceIn(-0.95f, 0.95f)

    companion object {
        private const val TAG = "AudioEngine"
        private const val CHUNK = 1024
        private const val SMOOTHING = 0.0015f

        @Volatile private var instance: AudioEngine? = null

        fun get(context: Context): AudioEngine =
            instance ?: synchronized(this) { instance ?: AudioEngine(context).also { instance = it } }
    }
}
