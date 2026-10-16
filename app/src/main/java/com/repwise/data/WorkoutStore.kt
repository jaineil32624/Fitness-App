package com.repwise.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class WorkoutRecord(
    val id: String = UUID.randomUUID().toString(),
    val exercise: String,
    val reps: Int,
    val seconds: Int,
    val calories: Int,
    val date: String = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
    val activeSeconds: Int = seconds,
)

data class UserProfile(
    val name: String = "Athlete",
    val weightKg: Float = 68f,
    val weeklyGoal: Int = 4,
    val difficulty: String = "Beginner",
    val githubOwner: String = "your-github-owner",
    val githubRepo: String = "repwise",
)

class WorkoutStore(context: Context) {
    private val preferences = context.getSharedPreferences("repwise_data", Context.MODE_PRIVATE)

    fun records(): List<WorkoutRecord> {
        val json = preferences.getString("workouts", "[]") ?: "[]"
        val array = JSONArray(json)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            WorkoutRecord(
                id = item.optString("id"),
                exercise = item.optString("exercise"),
                reps = item.optInt("reps"),
                seconds = item.optInt("seconds"),
                calories = item.optInt("calories"),
                date = item.optString("date"),
                activeSeconds = item.optInt("activeSeconds", item.optInt("seconds")),
            )
        }.sortedByDescending { it.date }
    }

    fun save(record: WorkoutRecord) {
        val all = records().toMutableList()
        all.add(0, record)
        val array = JSONArray()
        all.take(500).forEach { workout ->
            array.put(
                JSONObject()
                    .put("id", workout.id)
                    .put("exercise", workout.exercise)
                    .put("reps", workout.reps)
                    .put("seconds", workout.seconds)
                    .put("calories", workout.calories)
                    .put("date", workout.date)
                    .put("activeSeconds", workout.activeSeconds),
            )
        }
        preferences.edit().putString("workouts", array.toString()).apply()
    }

    fun profile(): UserProfile {
        val json = JSONObject(preferences.getString("profile", "{}") ?: "{}")
        return UserProfile(
            name = json.optString("name", "Athlete"),
            weightKg = json.optDouble("weightKg", 68.0).toFloat(),
            weeklyGoal = json.optInt("weeklyGoal", 4).coerceIn(1, 14),
            difficulty = json.optString("difficulty", "Beginner"),
            githubOwner = json.optString("githubOwner", "your-github-owner"),
            githubRepo = json.optString("githubRepo", "repwise"),
        )
    }

    fun saveProfile(profile: UserProfile) {
        val json = JSONObject()
            .put("name", profile.name)
            .put("weightKg", profile.weightKg)
            .put("weeklyGoal", profile.weeklyGoal)
            .put("difficulty", profile.difficulty)
            .put("githubOwner", profile.githubOwner)
            .put("githubRepo", profile.githubRepo)
        preferences.edit().putString("profile", json.toString()).apply()
    }
}
