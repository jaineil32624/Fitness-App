package com.repwise.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.repwise.audio.SavedMix
import com.repwise.domain.AccentTheme
import com.repwise.domain.AppSettings
import com.repwise.domain.Difficulty
import com.repwise.domain.IntervalConfig
import com.repwise.domain.ThemeMode
import com.repwise.domain.UserProfile
import com.repwise.domain.WorkoutRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek

/**
 * Local, app-private persistence for workouts, profile, and settings.
 *
 * Data is stored as JSON in private SharedPreferences using the same keys as v1 so existing
 * history carries over. Every read is defensive: a corrupt value falls back to defaults
 * instead of crashing the app on launch.
 */
class WorkoutStore private constructor(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences("repwise_data", Context.MODE_PRIVATE)

    private val _records = MutableStateFlow(readRecords())
    val records: StateFlow<List<WorkoutRecord>> = _records.asStateFlow()

    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _mixes = MutableStateFlow(readMixes())
    val mixes: StateFlow<List<SavedMix>> = _mixes.asStateFlow()

    fun save(record: WorkoutRecord) {
        val updated = (listOf(record) + _records.value).take(MAX_RECORDS)
        _records.value = updated
        val array = JSONArray()
        updated.forEach { array.put(it.toJson()) }
        preferences.edit { putString(KEY_WORKOUTS, array.toString()) }
    }

    fun delete(id: String) {
        _records.update { list -> list.filterNot { it.id == id } }
        val array = JSONArray()
        _records.value.forEach { array.put(it.toJson()) }
        preferences.edit { putString(KEY_WORKOUTS, array.toString()) }
    }

    fun saveProfile(profile: UserProfile) {
        _profile.value = profile
        val json = JSONObject()
            .put("name", profile.name)
            .put("weightKg", profile.weightKg.toDouble())
            .put("weeklyGoal", profile.weeklyGoal)
            .put("difficulty", profile.difficulty.label)
            .put("githubOwner", profile.githubOwner)
            .put("githubRepo", profile.githubRepo)
        preferences.edit { putString(KEY_PROFILE, json.toString()) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        val json = JSONObject()
            .put("onboardingDone", next.onboardingDone)
            .put("themeMode", next.themeMode.name)
            .put("accent", next.accent.name)
            .put("remindersEnabled", next.remindersEnabled)
            .put("reminderHour", next.reminderHour)
            .put("reminderMinute", next.reminderMinute)
            .put("planDays", JSONArray(next.planDays.map { it.name }))
            .put("soundEffects", next.soundEffects)
            .put("repSounds", next.repSounds)
            .put("haptics", next.haptics)
            .put("intervalWork", next.interval.workSeconds)
            .put("intervalRest", next.interval.restSeconds)
            .put("intervalRounds", next.interval.rounds)
        preferences.edit { putString(KEY_SETTINGS, json.toString()) }
    }

    fun saveMixes(mixes: List<SavedMix>) {
        _mixes.value = mixes
        val array = JSONArray()
        mixes.forEach { array.put(it.toJson()) }
        preferences.edit { putString(KEY_MIXES, array.toString()) }
    }

    fun clearHistory() {
        _records.value = emptyList()
        preferences.edit { remove(KEY_WORKOUTS) }
    }

    private fun readRecords(): List<WorkoutRecord> = safely(emptyList()) {
        val array = JSONArray(preferences.getString(KEY_WORKOUTS, "[]") ?: "[]")
        List(array.length()) { index -> array.optJSONObject(index) }
            .mapNotNull { item -> item?.toRecord() }
            .sortedWith(compareByDescending<WorkoutRecord> { it.date }.thenByDescending { it.startedAt })
    }

    private fun readProfile(): UserProfile = safely(UserProfile()) {
        val json = JSONObject(preferences.getString(KEY_PROFILE, "{}") ?: "{}")
        val defaults = UserProfile()
        val owner = json.optString("githubOwner", defaults.githubOwner)
        UserProfile(
            name = json.optString("name", defaults.name).ifBlank { defaults.name },
            weightKg = json.optDouble("weightKg", defaults.weightKg.toDouble()).toFloat().coerceIn(25f, 250f),
            weeklyGoal = json.optInt("weeklyGoal", defaults.weeklyGoal).coerceIn(1, 14),
            difficulty = Difficulty.parse(json.optString("difficulty")),
            // v1 shipped a placeholder owner that could never resolve; replace it with this project's repo.
            githubOwner = if (owner == "your-github-owner") defaults.githubOwner else owner,
            githubRepo = if (owner == "your-github-owner") defaults.githubRepo else json.optString("githubRepo", defaults.githubRepo),
        )
    }

    private fun readSettings(): AppSettings = safely(AppSettings()) {
        val raw = preferences.getString(KEY_SETTINGS, null)
        val defaults = AppSettings()
        if (raw == null) {
            // Users upgrading from v1 already have history; don't make them sit through onboarding.
            return@safely defaults.copy(onboardingDone = preferences.contains(KEY_WORKOUTS) || preferences.contains(KEY_PROFILE))
        }
        val json = JSONObject(raw)
        val days = json.optJSONArray("planDays")?.let { array ->
            (0 until array.length()).mapNotNull { runCatching { DayOfWeek.valueOf(array.getString(it)) }.getOrNull() }.toSet()
        } ?: defaults.planDays
        AppSettings(
            onboardingDone = json.optBoolean("onboardingDone", false),
            themeMode = enumOrDefault(json.optString("themeMode"), defaults.themeMode),
            accent = enumOrDefault(json.optString("accent"), defaults.accent),
            remindersEnabled = json.optBoolean("remindersEnabled", false),
            reminderHour = json.optInt("reminderHour", defaults.reminderHour).coerceIn(0, 23),
            reminderMinute = json.optInt("reminderMinute", defaults.reminderMinute).coerceIn(0, 59),
            planDays = days,
            soundEffects = json.optBoolean("soundEffects", defaults.soundEffects),
            repSounds = json.optBoolean("repSounds", defaults.repSounds),
            haptics = json.optBoolean("haptics", defaults.haptics),
            interval = IntervalConfig(
                json.optInt("intervalWork", defaults.interval.workSeconds),
                json.optInt("intervalRest", defaults.interval.restSeconds),
                json.optInt("intervalRounds", defaults.interval.rounds),
            ).sanitized(),
        )
    }

    private fun readMixes(): List<SavedMix> = safely(emptyList()) {
        val array = JSONArray(preferences.getString(KEY_MIXES, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(SavedMix::fromJson) }
    }

    private inline fun <T> safely(fallback: T, block: () -> T): T = try {
        block()
    } catch (error: JSONException) {
        Log.w(TAG, "Ignoring unreadable stored data", error)
        fallback
    } catch (error: ClassCastException) {
        Log.w(TAG, "Ignoring stored data with an unexpected type", error)
        fallback
    }

    companion object {
        private const val TAG = "WorkoutStore"
        private const val KEY_WORKOUTS = "workouts"
        private const val KEY_PROFILE = "profile"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_MIXES = "mixes"
        private const val MAX_RECORDS = 1000

        @Volatile private var instance: WorkoutStore? = null

        fun get(context: Context): WorkoutStore =
            instance ?: synchronized(this) { instance ?: WorkoutStore(context).also { instance = it } }

        private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default

        private fun WorkoutRecord.toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("exercise", exercise)
            .put("reps", reps)
            .put("seconds", seconds)
            .put("calories", calories)
            .put("date", date)
            .put("activeSeconds", activeSeconds)
            .put("startedAt", startedAt)
            .put("targetHit", targetHit)

        private fun JSONObject.toRecord(): WorkoutRecord? {
            val exercise = optString("exercise")
            if (exercise.isBlank()) return null
            return WorkoutRecord(
                id = optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                exercise = exercise,
                reps = optInt("reps").coerceAtLeast(0),
                seconds = optInt("seconds").coerceAtLeast(0),
                calories = optInt("calories").coerceAtLeast(0),
                date = optString("date"),
                activeSeconds = optInt("activeSeconds", optInt("seconds")).coerceAtLeast(0),
                startedAt = optLong("startedAt", 0L),
                targetHit = optBoolean("targetHit", false),
            )
        }
    }
}
