package com.repwise.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.repwise.MainActivity
import com.repwise.R
import com.repwise.RepwiseApplication
import com.repwise.data.WorkoutStore
import com.repwise.domain.AppSettings
import com.repwise.domain.Gamification
import com.repwise.domain.Stats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderScheduler {
    private const val REQUEST_CODE = 7
    private const val WINDOW_MS = 10 * 60 * 1000L

    /** Next planned reminder strictly after [now], or null when no days are planned. */
    fun nextTrigger(settings: AppSettings, now: LocalDateTime): LocalDateTime? {
        if (settings.planDays.isEmpty()) return null
        val time = LocalTime.of(settings.reminderHour, settings.reminderMinute)
        return (0L..7L).map { now.toLocalDate().plusDays(it).atTime(time) }
            .firstOrNull { it.isAfter(now) && it.dayOfWeek in settings.planDays }
    }

    fun sync(context: Context, settings: AppSettings) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarms.cancel(pending)
        if (!settings.remindersEnabled) return
        val next = nextTrigger(settings, LocalDateTime.now()) ?: return
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // An inexact window lets Android batch the wake-up with other alarms to save battery,
        // and needs no exact-alarm permission.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pending)
    }

    /** Opens the calendar app pre-filled with a weekly recurring workout on the planned days. */
    fun calendarIntent(settings: AppSettings): Intent {
        val days = settings.planDays.sorted().joinToString(",") { it.name.take(2) }
        val start = nextTrigger(settings, LocalDateTime.now()) ?: LocalDateTime.now().plusHours(1)
        val begin = ZonedDateTime.of(start, ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "Repwise workout")
            .putExtra(CalendarContract.Events.DESCRIPTION, "Camera-tracked workout with Repwise.")
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 30 * 60 * 1000L)
            .putExtra(CalendarContract.Events.RRULE, if (days.isEmpty()) "FREQ=WEEKLY" else "FREQ=WEEKLY;BYDAY=$days")
    }
}

/** Re-arms the reminder alarm after a reboot, app update, or clock change, which clear or skew alarms. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> ReminderScheduler.sync(context, WorkoutStore.get(context).settings.value)
        }
    }
}

/** Fires at the planned time; posts a nudge only if today's workout hasn't happened yet. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = WorkoutStore.get(context)
        val settings = store.settings.value
        if (settings.remindersEnabled) notify(context, store)
        ReminderScheduler.sync(context, settings)
    }

    private fun notify(context: Context, store: WorkoutStore) {
        val today = LocalDate.now()
        val records = store.records.value
        if (Stats.onDay(records, today).isNotEmpty()) return // Already trained today.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val streak = Stats.streak(records, today)
        val challenge = Gamification.challengeFor(today)
        val title = if (streak > 0) "Keep your $streak-day streak alive" else "Time to move"
        val text = "Today's challenge: ${challenge.title}."
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, RepwiseApplication.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_repwise)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(REMINDER_ID, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }

    private companion object { const val REMINDER_ID = 12 }
}

fun DayOfWeek.shortLabel(): String = name.take(1) + name.substring(1, 3).lowercase()
