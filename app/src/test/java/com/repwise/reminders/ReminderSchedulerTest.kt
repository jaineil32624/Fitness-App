package com.repwise.reminders

import com.repwise.domain.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ReminderSchedulerTest {
    private val settings = AppSettings(
        remindersEnabled = true,
        reminderHour = 18,
        reminderMinute = 30,
        planDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
    )

    @Test
    fun schedulesLaterTodayWhenTodayIsPlanned() {
        val monday = LocalDateTime.of(2026, 10, 12, 9, 0)
        assertEquals(LocalDateTime.of(2026, 10, 12, 18, 30), ReminderScheduler.nextTrigger(settings, monday))
    }

    @Test
    fun skipsToTheNextPlannedDayAfterTodaysTimePasses() {
        val mondayEvening = LocalDateTime.of(2026, 10, 12, 19, 0)
        assertEquals(LocalDateTime.of(2026, 10, 15, 18, 30), ReminderScheduler.nextTrigger(settings, mondayEvening))
    }

    @Test
    fun wrapsAroundTheWeek() {
        val friday = LocalDateTime.of(2026, 10, 16, 8, 0)
        assertEquals(LocalDateTime.of(2026, 10, 19, 18, 30), ReminderScheduler.nextTrigger(settings, friday))
    }

    @Test
    fun noPlannedDaysMeansNoReminder() {
        assertNull(ReminderScheduler.nextTrigger(settings.copy(planDays = emptySet()), LocalDateTime.of(2026, 10, 12, 9, 0)))
    }
}
