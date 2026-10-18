package com.repwise.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LevelInfo(val level: Int, val title: String, val xpIntoLevel: Int, val xpForNextLevel: Int, val totalXp: Int) {
    val progress: Float get() = if (xpForNextLevel == 0) 1f else xpIntoLevel / xpForNextLevel.toFloat()
}

enum class Badge(val title: String, val description: String) {
    FIRST_WORKOUT("First rep", "Finish your first workout"),
    FIVE_WORKOUTS("Getting started", "Finish 5 workouts"),
    TWENTY_FIVE_WORKOUTS("Regular", "Finish 25 workouts"),
    HUNDRED_WORKOUTS("Centurion", "Finish 100 workouts"),
    REPS_100("Hundred club", "Log 100 total reps"),
    REPS_1000("Rep machine", "Log 1,000 total reps"),
    STREAK_3("On a roll", "Work out 3 days in a row"),
    STREAK_7("Week warrior", "Work out 7 days in a row"),
    STREAK_30("Unstoppable", "Work out 30 days in a row"),
    PLANK_60("Rock solid", "Hold a plank for 60 seconds"),
    TARGETS_10("Bullseye", "Hit your session target 10 times"),
    EXPLORER("Explorer", "Try 5 different exercises"),
    ALL_ROUNDER("All-rounder", "Try every exercise"),
    EARLY_BIRD("Early bird", "Start a workout before 8 AM"),
    NIGHT_OWL("Night owl", "Start a workout after 9 PM"),
    WEEKLY_GOAL("Goal getter", "Hit your weekly workout goal"),
    CIRCUIT("Circuit breaker", "Complete an interval circuit"),
    CHALLENGER("Challenger", "Complete 5 daily challenges"),
}

enum class ChallengeKind { REPS_OF, PLANK_SECONDS, ACTIVE_MINUTES, DIFFERENT_EXERCISES, HIT_TARGET }

data class DailyChallenge(
    val date: LocalDate,
    val kind: ChallengeKind,
    val goal: Int,
    val exercise: ExerciseType? = null,
) {
    val title: String get() = when (kind) {
        ChallengeKind.REPS_OF -> "Do $goal ${exercise!!.title.lowercase()}"
        ChallengeKind.PLANK_SECONDS -> "Hold a plank for $goal seconds total"
        ChallengeKind.ACTIVE_MINUTES -> "Be active for $goal minutes"
        ChallengeKind.DIFFERENT_EXERCISES -> "Train $goal different exercises"
        ChallengeKind.HIT_TARGET -> "Hit your target in any session"
    }

    fun progress(dayRecords: List<WorkoutRecord>): Int = when (kind) {
        ChallengeKind.REPS_OF -> dayRecords.filter { it.type == exercise }.sumOf { it.reps }
        ChallengeKind.PLANK_SECONDS -> dayRecords.filter { it.type == ExerciseType.PLANK }.sumOf { it.reps }
        ChallengeKind.ACTIVE_MINUTES -> dayRecords.sumOf { it.activeSeconds } / 60
        ChallengeKind.DIFFERENT_EXERCISES -> dayRecords.map { it.exercise }.distinct().size
        ChallengeKind.HIT_TARGET -> if (dayRecords.any { it.targetHit }) 1 else 0
    }.coerceAtMost(goal)

    fun isComplete(dayRecords: List<WorkoutRecord>): Boolean = progress(dayRecords) >= goal
}

data class GameState(
    val level: LevelInfo,
    val badges: Set<Badge>,
    val challenge: DailyChallenge,
    val challengeProgress: Int,
    val challengesCompleted: Int,
)

object Gamification {
    const val CHALLENGE_XP = 40
    const val TARGET_BONUS_XP = 15

    private val titles = listOf("Rookie", "Mover", "Grinder", "Athlete", "Contender", "Champion", "Elite", "Legend")

    fun xpFor(record: WorkoutRecord): Int {
        val effort = when {
            record.isInterval -> record.reps * 6
            record.type?.isTimed == true -> record.reps / 2
            else -> record.reps * 2
        }
        val base = if (record.activeSeconds > 0 || record.reps > 0) 10 else 0
        return base + effort + record.activeSeconds / 10 + if (record.targetHit) TARGET_BONUS_XP else 0
    }

    /** Total XP needed to reach [level]: 0, 100, 300, 600, 1000, … */
    fun xpForLevel(level: Int): Int = 50 * level * (level - 1)

    fun levelFor(totalXp: Int): LevelInfo {
        var level = 1
        while (xpForLevel(level + 1) <= totalXp) level++
        val floor = xpForLevel(level)
        return LevelInfo(
            level = level,
            title = titles[((level - 1) / 2).coerceAtMost(titles.lastIndex)],
            xpIntoLevel = totalXp - floor,
            xpForNextLevel = xpForLevel(level + 1) - floor,
            totalXp = totalXp,
        )
    }

    fun challengeFor(date: LocalDate): DailyChallenge {
        val seed = date.toEpochDay().toInt()
        val kinds = ChallengeKind.entries
        return when (val kind = kinds[Math.floorMod(seed * 7 + 3, kinds.size)]) {
            ChallengeKind.REPS_OF -> {
                val options = ExerciseType.entries.filterNot { it.isTimed }
                DailyChallenge(date, kind, 20 + Math.floorMod(seed, 3) * 10, options[Math.floorMod(seed * 13, options.size)])
            }
            ChallengeKind.PLANK_SECONDS -> DailyChallenge(date, kind, 45 + Math.floorMod(seed, 4) * 15)
            ChallengeKind.ACTIVE_MINUTES -> DailyChallenge(date, kind, 5 + Math.floorMod(seed, 3) * 5)
            ChallengeKind.DIFFERENT_EXERCISES -> DailyChallenge(date, kind, 2 + Math.floorMod(seed, 2))
            ChallengeKind.HIT_TARGET -> DailyChallenge(date, kind, 1)
        }
    }

    fun completedChallengeDays(records: List<WorkoutRecord>): Int =
        records.groupBy { it.localDate }
            .count { (day, list) -> day != null && challengeFor(day).isComplete(list) }

    fun totalXp(records: List<WorkoutRecord>): Int =
        records.sumOf { xpFor(it) } + completedChallengeDays(records) * CHALLENGE_XP

    fun badges(
        records: List<WorkoutRecord>,
        weeklyGoal: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Set<Badge> {
        if (records.isEmpty()) return emptySet()
        val earned = mutableSetOf(Badge.FIRST_WORKOUT)
        if (records.size >= 5) earned += Badge.FIVE_WORKOUTS
        if (records.size >= 25) earned += Badge.TWENTY_FIVE_WORKOUTS
        if (records.size >= 100) earned += Badge.HUNDRED_WORKOUTS
        val reps = Stats.totalReps(records)
        if (reps >= 100) earned += Badge.REPS_100
        if (reps >= 1000) earned += Badge.REPS_1000
        val longest = Stats.longestStreak(records)
        if (longest >= 3) earned += Badge.STREAK_3
        if (longest >= 7) earned += Badge.STREAK_7
        if (longest >= 30) earned += Badge.STREAK_30
        if (records.any { it.type == ExerciseType.PLANK && it.reps >= 60 }) earned += Badge.PLANK_60
        if (records.count { it.targetHit } >= 10) earned += Badge.TARGETS_10
        val tried = records.mapNotNull { it.type }.toSet()
        if (tried.size >= 5) earned += Badge.EXPLORER
        if (tried.size == ExerciseType.entries.size) earned += Badge.ALL_ROUNDER
        val hours = records.filter { it.startedAt > 0 }.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).hour }
        if (hours.any { it < 8 }) earned += Badge.EARLY_BIRD
        if (hours.any { it >= 21 }) earned += Badge.NIGHT_OWL
        val goalMet = records.mapNotNull { it.localDate }
            .groupingBy { Stats.weekStart(it) }
            .eachCount()
            .any { it.value >= weeklyGoal }
        if (goalMet) earned += Badge.WEEKLY_GOAL
        if (records.any { it.isInterval && it.reps > 0 }) earned += Badge.CIRCUIT
        if (completedChallengeDays(records) >= 5) earned += Badge.CHALLENGER
        return earned
    }

    fun state(records: List<WorkoutRecord>, weeklyGoal: Int, today: LocalDate = LocalDate.now()): GameState {
        val challenge = challengeFor(today)
        return GameState(
            level = levelFor(totalXp(records)),
            badges = badges(records, weeklyGoal),
            challenge = challenge,
            challengeProgress = challenge.progress(Stats.onDay(records, today)),
            challengesCompleted = completedChallengeDays(records),
        )
    }

    fun unlockedAccents(level: Int): List<AccentTheme> = AccentTheme.entries.filter { level >= it.unlockLevel }
}

/** What changed after saving a workout, for the celebration screen. */
data class SessionReward(
    val record: WorkoutRecord,
    val xpEarned: Int,
    val before: LevelInfo,
    val after: LevelInfo,
    val newBadges: List<Badge>,
    val challengeCompleted: Boolean,
    val newThemes: List<AccentTheme>,
) {
    val leveledUp: Boolean get() = after.level > before.level

    companion object {
        fun compute(before: List<WorkoutRecord>, after: List<WorkoutRecord>, record: WorkoutRecord, weeklyGoal: Int): SessionReward {
            val beforeLevel = Gamification.levelFor(Gamification.totalXp(before))
            val afterLevel = Gamification.levelFor(Gamification.totalXp(after))
            val day = record.localDate ?: LocalDate.now()
            val challenge = Gamification.challengeFor(day)
            val wasComplete = challenge.isComplete(Stats.onDay(before, day))
            val nowComplete = challenge.isComplete(Stats.onDay(after, day))
            val oldBadges = Gamification.badges(before, weeklyGoal)
            return SessionReward(
                record = record,
                xpEarned = afterLevel.totalXp - beforeLevel.totalXp,
                before = beforeLevel,
                after = afterLevel,
                newBadges = Gamification.badges(after, weeklyGoal).filterNot { it in oldBadges },
                challengeCompleted = nowComplete && !wasComplete,
                newThemes = AccentTheme.entries.filter { it.unlockLevel in (beforeLevel.level + 1)..afterLevel.level },
            )
        }
    }
}
