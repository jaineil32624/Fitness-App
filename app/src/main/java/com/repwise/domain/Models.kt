package com.repwise.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

enum class MuscleGroup(val label: String) {
    LEGS("Legs"), UPPER("Upper body"), CORE("Core"), CARDIO("Cardio"),
}

enum class ExerciseType(
    val title: String,
    val met: Float,
    val group: MuscleGroup,
    val cue: String,
    val isTimed: Boolean = false,
) {
    SQUAT("Squats", 5.0f, MuscleGroup.LEGS, "Face the camera, feet shoulder-width apart."),
    PUSH_UP("Push-ups", 7.0f, MuscleGroup.UPPER, "Turn sideways so your whole body is in frame."),
    JUMPING_JACK("Jumping jacks", 8.0f, MuscleGroup.CARDIO, "Face the camera with room to spread your arms."),
    LUNGE("Lunges", 5.0f, MuscleGroup.LEGS, "Turn slightly sideways and step back into each lunge."),
    PLANK("Plank", 3.5f, MuscleGroup.CORE, "Turn sideways; keep shoulders, hips, and ankles in frame.", isTimed = true),
    CRUNCH("Crunches", 4.0f, MuscleGroup.CORE, "Lie sideways to the camera with knees bent."),
    SHOULDER_PRESS("Shoulder presses", 5.0f, MuscleGroup.UPPER, "Face the camera with elbows at shoulder height."),
    MOUNTAIN_CLIMBER("Mountain climbers", 8.0f, MuscleGroup.CARDIO, "Turn sideways in a high plank."),
    GLUTE_BRIDGE("Glute bridges", 4.0f, MuscleGroup.LEGS, "Lie sideways to the camera, feet flat on the floor."),
    HIGH_KNEES("High knees", 8.0f, MuscleGroup.CARDIO, "Face the camera and jog in place."),
    ;

    val isHighIntensity: Boolean get() = met >= 7f

    /** Reps (or seconds held for timed moves) to aim for in one session. */
    fun target(difficulty: Difficulty): Int = if (isTimed) {
        when (difficulty) { Difficulty.BEGINNER -> 20; Difficulty.INTERMEDIATE -> 40; Difficulty.ADVANCED -> 60 }
    } else {
        when (difficulty) { Difficulty.BEGINNER -> 10; Difficulty.INTERMEDIATE -> 15; Difficulty.ADVANCED -> 25 }
    }

    companion object {
        fun fromTitle(title: String): ExerciseType? = entries.firstOrNull { it.title == title }
    }
}

enum class Difficulty(val label: String, val weeklyMinutesGoal: Int) {
    BEGINNER("Beginner", 45), INTERMEDIATE("Intermediate", 90), ADVANCED("Advanced", 150);

    companion object {
        fun parse(value: String?): Difficulty = entries.firstOrNull { it.label == value || it.name == value } ?: BEGINNER
    }
}

const val INTERVAL_TITLE = "Interval circuit"
private const val INTERVAL_MET = 8f

data class WorkoutRecord(
    val id: String = UUID.randomUUID().toString(),
    val exercise: String,
    val reps: Int,
    val seconds: Int,
    val calories: Int,
    val date: String = LocalDate.now().toString(),
    val activeSeconds: Int = seconds,
    /** Wall-clock start time in epoch millis; 0 for records saved before v2. */
    val startedAt: Long = 0L,
    val targetHit: Boolean = false,
) {
    val type: ExerciseType? get() = ExerciseType.fromTitle(exercise)
    val isInterval: Boolean get() = exercise == INTERVAL_TITLE
    val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()

    fun movementLabel(): String = when {
        isInterval -> "$reps rounds"
        type?.isTimed == true -> "$reps sec held"
        else -> "$reps reps"
    }
}

fun estimateCalories(met: Float, weightKg: Float, activeSeconds: Int): Int =
    (met * weightKg * (activeSeconds / 3600f)).toInt()

fun intervalCalories(weightKg: Float, activeSeconds: Int): Int = estimateCalories(INTERVAL_MET, weightKg, activeSeconds)

data class UserProfile(
    val name: String = "Athlete",
    val weightKg: Float = 68f,
    val weeklyGoal: Int = 4,
    val difficulty: Difficulty = Difficulty.BEGINNER,
    val githubOwner: String = DEFAULT_GITHUB_OWNER,
    val githubRepo: String = DEFAULT_GITHUB_REPO,
) {
    companion object {
        const val DEFAULT_GITHUB_OWNER = "jaineil32624"
        const val DEFAULT_GITHUB_REPO = "Fitness-App"
    }
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class AccentTheme(val label: String, val unlockLevel: Int) {
    FOREST("Forest", 1),
    OCEAN("Ocean", 3),
    SUNSET("Sunset", 5),
    GRAPE("Grape", 7),
    GOLD("Champion gold", 10),
}

data class IntervalConfig(val workSeconds: Int = 40, val restSeconds: Int = 20, val rounds: Int = 8) {
    fun sanitized() = IntervalConfig(workSeconds.coerceIn(5, 600), restSeconds.coerceIn(0, 600), rounds.coerceIn(1, 50))
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentTheme = AccentTheme.FOREST,
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
    val planDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
    val soundEffects: Boolean = true,
    val repSounds: Boolean = false,
    val haptics: Boolean = true,
    val interval: IntervalConfig = IntervalConfig(),
)
