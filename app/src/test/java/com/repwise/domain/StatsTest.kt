package com.repwise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsTest {
    private val today = LocalDate.of(2026, 10, 14) // A Wednesday.

    private fun record(daysAgo: Long, exercise: ExerciseType = ExerciseType.SQUAT, reps: Int = 10, active: Int = 60) =
        WorkoutRecord(exercise = exercise.title, reps = reps, seconds = active, calories = 5, date = today.minusDays(daysAgo).toString(), activeSeconds = active)

    @Test
    fun streakCountsConsecutiveDaysEndingToday() {
        assertEquals(3, Stats.streak(listOf(record(0), record(1), record(2), record(4)), today))
    }

    @Test
    fun streakSurvivesUntilTheEndOfTodayIfYesterdayWasTrained() {
        assertEquals(2, Stats.streak(listOf(record(1), record(2)), today))
    }

    @Test
    fun streakBreaksAfterAMissedDay() {
        assertEquals(0, Stats.streak(listOf(record(2), record(3)), today))
    }

    @Test
    fun longestStreakFindsTheBestRunAndIgnoresDuplicateDays() {
        val records = listOf(record(10), record(9), record(9), record(8), record(7), record(3), record(2))
        assertEquals(4, Stats.longestStreak(records))
    }

    @Test
    fun weekIsMondayThroughSunday() {
        assertEquals(LocalDate.of(2026, 10, 12), Stats.weekStart(today))
        // Two days ago is Monday (in this week); three days ago is last Sunday (not in this week).
        assertEquals(1, Stats.inWeek(listOf(record(2), record(3)), today).size)
    }

    @Test
    fun weekMinutesHasSevenDaysWithTotals() {
        val minutes = Stats.weekMinutes(listOf(record(0, active = 120), record(0, active = 60), record(1, active = 300)), today)
        assertEquals(7, minutes.size)
        assertEquals(5, minutes[1].second) // Tuesday
        assertEquals(3, minutes[2].second) // Wednesday
    }

    @Test
    fun monthCountsOnlyIncludeTheRequestedMonth() {
        val counts = Stats.monthCounts(listOf(record(0), record(0), record(20)), YearMonth.from(today))
        assertEquals(mapOf(today to 2), counts)
    }

    @Test
    fun totalRepsExcludesTimedHoldsAndIntervals() {
        val records = listOf(
            record(0, ExerciseType.SQUAT, reps = 12),
            record(0, ExerciseType.PLANK, reps = 60),
            WorkoutRecord(exercise = INTERVAL_TITLE, reps = 8, seconds = 300, calories = 30, date = today.toString()),
        )
        assertEquals(12, Stats.totalReps(records))
    }

    @Test
    fun fitnessScoreIsBoundedAndRewardsConsistency() {
        val profile = UserProfile(weeklyGoal = 3, difficulty = Difficulty.BEGINNER)
        assertEquals(0, Stats.fitnessScore(emptyList(), profile, today))
        val strong = (0L..2L).map { record(it, active = 20 * 60) }
        val score = Stats.fitnessScore(strong, profile, today)
        assertTrue(score in 1..100)
        assertEquals(50 + 30 + (3 * 20 / 7), score)
    }

    @Test
    fun coachSuggestsUntriedMovesAcrossDifferentGroups() {
        val suggestions = Coach.suggestions(listOf(record(0, ExerciseType.SQUAT)), Difficulty.INTERMEDIATE, today)
        assertEquals(3, suggestions.size)
        assertTrue(suggestions.none { it.exercise == ExerciseType.SQUAT })
        assertEquals(3, suggestions.map { it.exercise.group }.distinct().size)
    }
}
