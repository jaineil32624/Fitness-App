package com.repwise.ui.profile

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repwise.BuildConfig
import com.repwise.domain.AccentTheme
import com.repwise.domain.Difficulty
import com.repwise.domain.ThemeMode
import com.repwise.reminders.ReminderScheduler
import com.repwise.reminders.shortLabel
import com.repwise.ui.AppViewModel
import com.repwise.ui.components.RepwiseCard
import com.repwise.ui.theme.LocalRepwiseColors
import com.repwise.ui.theme.swatch
import java.time.DayOfWeek
import java.util.Locale

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 24.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(LocalRepwiseColors.current.heroBrush),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(profile.name.take(1).uppercase(Locale.getDefault()), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(profile.name, style = MaterialTheme.typography.headlineSmall)
                    Text("Level ${game.level.level} ${game.level.title} · ${records.size} sessions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { TrainingProfileCard(vm) }
        item { AppearanceCard(vm) }
        item { PlanCard(vm) }
        item { FeedbackCard(vm) }
        item { UpdatesCard(vm) }
        item { PrivacyCard(vm) }
        item {
            Text(
                "Repwise ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    RepwiseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun TrainingProfileCard(vm: AppViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var name by rememberSaveable(profile.name) { mutableStateOf(profile.name) }
    var weight by rememberSaveable(profile.weightKg) { mutableStateOf(profile.weightKg.toInt().toString()) }
    var goal by rememberSaveable(profile.weeklyGoal) { mutableStateOf(profile.weeklyGoal.toString()) }
    var difficulty by rememberSaveable(profile.difficulty) { mutableStateOf(profile.difficulty) }
    val weightValid = weight.toIntOrNull()?.let { it in 25..250 } == true
    val goalValid = goal.toIntOrNull()?.let { it in 1..14 } == true

    Section("Training profile") {
        OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                weight, { weight = it.filter(Char::isDigit).take(3) }, label = { Text("Weight (kg)") },
                isError = !weightValid, supportingText = { if (!weightValid) Text("25–250") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                goal, { goal = it.filter(Char::isDigit).take(2) }, label = { Text("Workouts / week") },
                isError = !goalValid, supportingText = { if (!goalValid) Text("1–14") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
            )
        }
        Text("Difficulty sets your rep targets and weekly active-minute goal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Difficulty.entries.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = difficulty == level, onClick = { difficulty = level },
                    shape = SegmentedButtonDefaults.itemShape(index, Difficulty.entries.size),
                ) { Text(level.label, maxLines = 1) }
            }
        }
        Button(
            enabled = weightValid && goalValid,
            onClick = {
                vm.saveProfile(
                    profile.copy(
                        name = name.trim().ifBlank { "Athlete" },
                        weightKg = weight.toFloat(),
                        weeklyGoal = goal.toInt(),
                        difficulty = difficulty,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save profile") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceCard(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    Section("Appearance") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = settings.themeMode == mode,
                    onClick = { vm.updateSettings { it.copy(themeMode = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                ) { Text(mode.label) }
            }
        }
        Text("Themes unlock as you level up.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentTheme.entries.forEach { accent ->
                val unlocked = game.level.level >= accent.unlockLevel
                val selected = settings.accent == accent && unlocked
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(accent.swatch())
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.IconButton(onClick = { vm.selectAccent(accent) }, modifier = Modifier.size(48.dp)) {
                            when {
                                selected -> Icon(Icons.Default.Check, "${accent.label} selected", tint = Color.White)
                                !unlocked -> Icon(Icons.Default.Lock, "${accent.label} locked", tint = Color.White.copy(alpha = 0.85f))
                                else -> Unit
                            }
                        }
                    }
                    Text(
                        if (unlocked) accent.label else "Lv ${accent.unlockLevel}",
                        style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlanCard(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var pickingTime by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(remindersEnabled = granted) }
        if (!granted) vm.message("Allow notifications in Settings to get workout reminders.")
    }

    Section("Training plan & reminders") {
        Text("Pick your training days. They shape reminders, the calendar view, and Home suggestions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                val selected = day in settings.planDays
                FilterChip(
                    selected = selected,
                    onClick = { vm.updateSettings { it.copy(planDays = if (selected) it.planDays - day else it.planDays + day) } },
                    label = { Text(day.shortLabel()) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Workout reminders", style = MaterialTheme.typography.titleSmall)
                Text("Only on planned days you haven't trained yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = settings.remindersEnabled,
                onCheckedChange = { enable ->
                    val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (enable && needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.updateSettings { it.copy(remindersEnabled = enable) }
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { pickingTime = true }, enabled = settings.remindersEnabled) {
                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(formatTime(settings.reminderHour, settings.reminderMinute))
            }
            OutlinedButton(onClick = {
                try {
                    context.startActivity(ReminderScheduler.calendarIntent(settings))
                } catch (_: ActivityNotFoundException) {
                    vm.message("No calendar app is installed.")
                }
            }) {
                Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add to calendar")
            }
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(settings.reminderHour, settings.reminderMinute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text("Reminder time") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSettings { it.copy(reminderHour = state.hour, reminderMinute = state.minute) }
                    pickingTime = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    return String.format(Locale.getDefault(), "%d:%02d %s", h, minute, if (hour < 12) "AM" else "PM")
}

@Composable
private fun FeedbackCard(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Section("Sound & haptics") {
        ToggleRow("Sound effects", "Chimes for targets, rounds, and achievements", settings.soundEffects) { on -> vm.updateSettings { it.copy(soundEffects = on) } }
        ToggleRow("Rep tick", "A soft click for every counted rep", settings.repSounds, enabled = settings.soundEffects) { on -> vm.updateSettings { it.copy(repSounds = on) } }
        ToggleRow("Haptics", "Vibrate on reps, targets, and interval changes", settings.haptics) { on -> vm.updateSettings { it.copy(haptics = on) } }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun UpdatesCard(vm: AppViewModel) {
    val context = LocalContext.current
    val update by vm.update.collectAsStateWithLifecycle()
    Section("App updates") {
        Text("Check for a newer APK in the latest GitHub release. Updates are never installed silently.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(update.owner, { vm.setUpdateRepo(it.trim(), update.repo) }, label = { Text("Owner") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(update.repo, { vm.setUpdateRepo(update.owner, it.trim()) }, label = { Text("Repository") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !update.checking, onClick = vm::checkForUpdate) {
                if (update.checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Check releases")
            }
            update.release?.let { release ->
                OutlinedButton(enabled = !update.downloading, onClick = vm::downloadUpdate) {
                    if (update.downloading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Download ${release.tag}")
                }
            }
        }
        if (update.downloaded && update.downloadId != null) {
            Button(onClick = { install(context, update.downloadId!!, vm::message) }, modifier = Modifier.fillMaxWidth()) { Text("Install downloaded APK") }
        }
    }
}

private fun install(context: Context, downloadId: Long, onMessage: (String) -> Unit) {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val apkUri = manager.getUriForDownloadedFile(downloadId)
    when {
        apkUri == null -> onMessage("The downloaded APK couldn't be found. Please download it again.")
        !context.packageManager.canRequestPackageInstalls() -> {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            onMessage("Allow installs for Repwise, return here, then tap Install again.")
        }
        else -> try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(apkUri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (error: ActivityNotFoundException) {
            onMessage("Android couldn't open the APK installer.")
        }
    }
}

@Composable
private fun PrivacyCard(vm: AppViewModel) {
    var confirmClear by remember { mutableStateOf(false) }
    Section("Data & privacy") {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                "Camera frames are analyzed on this device and never stored or uploaded. Workout history lives in app-private storage. GitHub is contacted only when you check for an update.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = { confirmClear = true }, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text("Clear workout history")
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("This permanently deletes every saved session, and with it your XP, level, badges, and streaks. Your profile and settings are kept.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Delete everything") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
