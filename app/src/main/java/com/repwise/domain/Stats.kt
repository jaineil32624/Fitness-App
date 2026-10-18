package com.repwise.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Pure aggregation helpers over workout history. Everything here is unit-testable. */
object Stats {
    fun streak(records: List<WorkoutRecord>, today: LocalDate = LocalDate.now()): Int {
        val dates = records.mapNotNullTo(HashSet()) { it.localDate }
        var day = if (today in dates) today else today.minusDays(1)
        var count = 0
        while (day in dates) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun longestStreak(records: List<WorkoutRecord>): Int {
        val days = records.mapNotNull { it.localDate }.distinct().sorted()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous != null && ChronoUnit.DAYS.between(previous, day) == 1L) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    /** Monday-based calendar week containing [today]. */
    fun weekStart(today: LocalDate = LocalDate.now()): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun inWeek(records: List<WorkoutRecord>, today: LocalDate = LocalDate.now()): List<WorkoutRecord> {
        val start = weekStart(today)
        val end = start.plusDays(6)
        return records.filter { r -> r.localDate?.let { it in start..end } == true }
    }

    fun onDay(records: List<WorkoutRecord>, day: LocalDate): List<WorkoutRecord> =
        records.filter { it.localDate == day }

    /** Active minutes for each day Monday..Sunday of the week containing [today]. */
    fun weekMinutes(records: List<WorkoutRecord>, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Int>> {
        val start = weekStart(today)
        return (0L..6L).map { offset ->
            val day = start.plusDays(offset)
            day to onDay(records, day).sumOf { it.activeSeconds } / 60
        }
    }

    fun monthCounts(records: List<WorkoutRecord>, month: YearMonth): Map<LocalDate, Int> =
        records.mapNotNull { it.localDate }.filter { YearMonth.from(it) == month }.groupingBy { it }.eachCount()

    fun totalReps(records: List<WorkoutRecord>): Int =
        records.filter { it.type != null && it.type?.isTimed != true }.sumOf { it.reps }

    fun personalBests(records: List<WorkoutRecord>): Map<ExerciseType, Int> =
        records.mapNotNull { r -> r.type?.let { it to r.reps } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, reps) -> reps.max() }

    fun repsByExercise(records: List<WorkoutRecord>): List<Pair<String, Int>> =
        records.groupBy { it.exercise }
            .map { (name, list) -> name to list.sumOf { it.reps } }
            .sortedByDescending { it.second }

    /** Most common hour of day the user starts workouts, if there's enough history to tell. */
    fun usualHour(records: List<WorkoutRecord>, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Int? {
        val hours = records.filter { it.startedAt > 0 }
            .map { java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).hour }
        if (hours.size < 3) return null
        return hours.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    /**
     * Weekly fitness score, 0–100: half for hitting the weekly session goal, 30% for active
     * minutes against the difficulty's minutes goal, and 20% for keeping a streak going.
     */
    fun fitnessScore(records: List<WorkoutRecord>, profile: UserProfile, today: LocalDate = LocalDate.now()): Int {
        val week = inWeek(records, today)
        val sessions = (week.size.toFloat() / profile.weeklyGoal.coerceAtLeast(1)).coerceAtMost(1f)
        val minutes = (week.sumOf { it.activeSeconds } / 60f / profile.difficulty.weeklyMinutesGoal).coerceAtMost(1f)
        val streak = (streak(records, today).coerceAtMost(7) / 7f)
        return (sessions * 50 + minutes * 30 + streak * 20).toInt().coerceIn(0, 100)
    }
}

data class Suggestion(val exercise: ExerciseType, val reason: String)

/** Picks exercises that keep training balanced: neglected moves and muscle groups first. */
object Coach {
    fun suggestions(
        records: List<WorkoutRecord>,
        difficulty: Difficulty,
        today: LocalDate = LocalDate.now(),
        count: Int = 3,
    ): List<Suggestion> {
        val lastDone = HashMap<ExerciseType, LocalDate>()
        records.forEach { r ->
            val type = r.type ?: return@forEach
            val date = r.localDate ?: return@forEach
            if (lastDone[type]?.isBefore(date) != false) lastDone[type] = date
        }
        val recentGroups = Stats.inWeek(records, today).mapNotNull { it.type?.group }.groupingBy { it }.eachCount()
        val scored = ExerciseType.entries.map { exercise ->
            val last = lastDone[exercise]
            val daysSince = last?.let { ChronoUnit.DAYS.between(it, today).toInt() } ?: 30
            val groupLoad = recentGroups[exercise.group] ?: 0
            var score = daysSince * 2 - groupLoad * 5
            if (difficulty == Difficulty.BEGINNER && exercise.isHighIntensity) score -= 4
            if (difficulty == Difficulty.ADVANCED && exercise.isHighIntensity) score += 4
            val reason = when {
                last == null -> "New for you — give it a try"
                groupLoad == 0 -> "No ${exercise.group.label.lowercase()} work yet this week"
                daysSince >= 3 -> "Last done $daysSince days ago"
                else -> "Keeps your ${exercise.group.label.lowercase()} training going"
            }
            Triple(exercise, score, reason)
        }
        return scored.sortedByDescending { it.second }
            .distinctBy { it.first.group }
            .plus(scored.sortedByDescending { it.second })
            .distinctBy { it.first }
            .take(count)
            .map { Suggestion(it.first, it.third) }
    }
}
