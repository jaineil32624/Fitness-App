package com.repwise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class GamificationTest {
    private val day = LocalDate.of(2026, 10, 14)

    private fun squats(reps: Int, active: Int = 60, date: LocalDate = day, targetHit: Boolean = false) =
        WorkoutRecord(exercise = ExerciseType.SQUAT.title, reps = reps, seconds = active, calories = 5, date = date.toString(), activeSeconds = active, targetHit = targetHit)

    @Test
    fun levelThresholdsGrowQuadratically() {
        assertEquals(0, Gamification.xpForLevel(1))
        assertEquals(100, Gamification.xpForLevel(2))
        assertEquals(300, Gamification.xpForLevel(3))
        assertEquals(1000, Gamification.xpForLevel(5))
    }

    @Test
    fun levelInfoReportsProgressWithinTheLevel() {
        val info = Gamification.levelFor(150)
        assertEquals(2, info.level)
        assertEquals(50, info.xpIntoLevel)
        assertEquals(200, info.xpForNextLevel)
        assertEquals(0.25f, info.progress, 0.0001f)
    }

    @Test
    fun xpRewardsRepsActiveTimeAndTargets() {
        assertEquals(10 + 20 + 6, Gamification.xpFor(squats(10, active = 60)))
        assertEquals(10 + 20 + 6 + Gamification.TARGET_BONUS_XP, Gamification.xpFor(squats(10, active = 60, targetHit = true)))
        assertEquals(0, Gamification.xpFor(squats(0, active = 0)))
    }

    @Test
    fun timedHoldsEarnLessPerSecondThanRepsEarnPerRep() {
        val plank = WorkoutRecord(exercise = ExerciseType.PLANK.title, reps = 60, seconds = 60, calories = 3, date = day.toString(), activeSeconds = 60)
        assertEquals(10 + 30 + 6, Gamification.xpFor(plank))
    }

    @Test
    fun dailyChallengeIsDeterministicAndVariesByDay() {
        assertEquals(Gamification.challengeFor(day), Gamification.challengeFor(day))
        val kinds = (0L until 14L).map { Gamification.challengeFor(day.plusDays(it)).kind }.toSet()
        assertTrue("challenges should rotate", kinds.size >= 3)
    }

    @Test
    fun challengeProgressIsCappedAtGoal() {
        val challenge = DailyChallenge(day, ChallengeKind.REPS_OF, 20, ExerciseType.SQUAT)
        assertEquals(15, challenge.progress(listOf(squats(15))))
        assertEquals(20, challenge.progress(listOf(squats(15), squats(15))))
        assertTrue(challenge.isComplete(listOf(squats(15), squats(15))))
    }

    @Test
    fun badgesFollowHistory() {
        assertTrue(Gamification.badges(emptyList(), 3).isEmpty())
        val week = (0L until 7L).map { squats(20, date = day.minusDays(it)) }
        val badges = Gamification.badges(week, weeklyGoal = 3, zone = ZoneOffset.UTC)
        assertTrue(Badge.FIRST_WORKOUT in badges)
        assertTrue(Badge.FIVE_WORKOUTS in badges)
        assertTrue(Badge.STREAK_7 in badges)
        assertTrue(Badge.REPS_100 in badges)
        assertFalse(Badge.EXPLORER in badges)
    }

    @Test
    fun earlyBirdUsesStartTime() {
        val morning = day.atTime(6, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        val record = squats(5).copy(startedAt = morning)
        assertTrue(Badge.EARLY_BIRD in Gamification.badges(listOf(record), 3, ZoneOffset.UTC))
    }

    @Test
    fun sessionRewardReportsLevelUpsAndNewBadges() {
        // 56 XP (+40 if the day's challenge is met) stays in level 1; adding 120 XP crosses 100.
        val before = listOf(squats(20, active = 60))
        val record = squats(40, active = 300)
        val reward = SessionReward.compute(before, before + record, record, weeklyGoal = 3)
        assertTrue(reward.xpEarned >= Gamification.xpFor(record))
        assertTrue(reward.leveledUp)
        assertFalse(Badge.FIRST_WORKOUT in reward.newBadges)
    }

    @Test
    fun accentThemesUnlockByLevel() {
        assertEquals(listOf(AccentTheme.FOREST), Gamification.unlockedAccents(1))
        assertEquals(AccentTheme.entries, Gamification.unlockedAccents(10))
    }
}
