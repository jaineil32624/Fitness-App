package com.repwise

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class RepwiseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Nudges on your planned training days"
                },
                NotificationChannel(CHANNEL_PLAYBACK, "Music playback", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Controls for workout music and ambient sounds"
                    setShowBadge(false)
                },
            ),
        )
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_PLAYBACK = "playback"
    }
}
