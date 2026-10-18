package com.repwise.ui.progress

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repwise.domain.AppSettings
import com.repwise.domain.Badge
import com.repwise.domain.GameState
import com.repwise.domain.Gamification
import com.repwise.domain.Stats
import com.repwise.domain.UserProfile
import com.repwise.domain.WorkoutRecord
import com.repwise.reminders.shortLabel
import com.repwise.ui.AppViewModel
import com.repwise.ui.components.IconBubble
import com.repwise.ui.components.LinearMeter
import com.repwise.ui.components.Pill
import com.repwise.ui.components.ProgressRing
import com.repwise.ui.components.RepwiseCard
import com.repwise.ui.components.SectionHeader
import com.repwise.ui.components.StatTile
import com.repwise.ui.components.formatDuration
import com.repwise.ui.components.icon
import com.repwise.ui.components.staggeredEntrance
import com.repwise.ui.theme.LocalRepwiseColors
import com.repwise.ui.theme.overline
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class Range(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month") }

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val records by vm.records.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(Range.WEEK) }
    var pendingDelete by remember { mutableStateOf<WorkoutRecord?>(null) }
    val streak = remember(records) { Stats.streak(records) }
    val longest = remember(records) { Stats.longestStreak(records) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 24.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Progress", style = MaterialTheme.typography.headlineMedium)
                Text("Small steps add up. Keep showing up.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { LevelCard(game, Modifier.staggeredEntrance(0)) }
        item {
            Row(Modifier.staggeredEntrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("STREAK", streak, "days", Modifier.weight(1f))
                StatTile("LONGEST", longest, "day streak", Modifier.weight(1f))
                StatTile("SESSIONS", records.size, "all time", Modifier.weight(1f))
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Range.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = range == option,
                        onClick = { range = option },
                        shape = SegmentedButtonDefaults.itemShape(index, Range.entries.size),
                    ) { Text(option.label) }
                }
            }
        }
        item {
            AnimatedContent(range, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "range") { selected ->
                when (selected) {
                    Range.DAY -> DayView(records)
                    Range.WEEK -> WeekView(records, profile, settings)
                    Range.MONTH -> MonthView(records, settings)
                }
            }
        }
        item { InsightsCard(records) }
        item { SectionHeader("Badges · ${game.badges.size}/${Badge.entries.size}") }
        item { BadgeGrid(game.badges) }
        item { SectionHeader("History") }
        if (records.isEmpty()) {
            item {
                Text(
                    "Your completed sessions will show up here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        items(records.take(50), key = { it.id }) { record ->
            HistoryRow(record, Modifier.animateItem(), onDelete = { pendingDelete = record })
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this session?") },
            text = { Text("${record.exercise} on ${record.date}. XP, streaks, and badges are recalculated from your remaining history.") },
            confirmButton = { TextButton(onClick = { vm.deleteRecord(record.id); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LevelCard(game: GameState, modifier: Modifier = Modifier) {
    val colors = LocalRepwiseColors.current
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(colors.heroBrush)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = game.level.progress, size = 92.dp, stroke = 9.dp,
                track = Color.White.copy(alpha = 0.15f),
                brush = Brush.sweepGradient(listOf(colors.glow, Color.White, colors.glow)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LVL", style = MaterialTheme.typography.overline, color = Color.White.copy(alpha = 0.7f))
                    Text("${game.level.level}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(game.level.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    "${game.level.xpForNextLevel - game.level.xpIntoLevel} XP to level ${game.level.level + 1}",
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text("${game.level.totalXp} total XP · ${game.challengesCompleted} challenges", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DayView(records: List<WorkoutRecord>) {
    var day by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val date = LocalDate.ofEpochDay(day)
    val sessions = remember(records, day) { Stats.onDay(records, date) }
    val challenge = remember(day) { Gamification.challengeFor(date) }
    RepwiseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Navigator(
                label = if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                onPrevious = { day-- },
                onNext = { day++ },
                nextEnabled = date.isBefore(LocalDate.now()),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                MiniStat("Sessions", "${sessions.size}")
                MiniStat("Active", formatDuration(sessions.sumOf { it.activeSeconds }))
                MiniStat("kcal", "${sessions.sumOf { it.calories }}")
                MiniStat("XP", "${sessions.sumOf { Gamification.xpFor(it) }}")
            }
            Spacer(Modifier.height(12.dp))
            val progress = challenge.progress(sessions)
            Text("CHALLENGE", style = MaterialTheme.typography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(challenge.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            LinearMeter(progress / challenge.goal.toFloat(), height = 8.dp)
            if (sessions.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (date == LocalDate.now()) "No sessions yet today." else "Rest day.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
            sessions.forEach { record ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(record.type?.icon() ?: Icons.Default.Timer, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(record.exercise, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(record.movementLabel(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun WeekView(records: List<WorkoutRecord>, profile: UserProfile, settings: AppSettings) {
    var offset by rememberSaveable { mutableLongStateOf(0L) }
    val anchor = LocalDate.now().plusWeeks(offset)
    val minutes = remember(records, offset) { Stats.weekMinutes(records, anchor) }
    val week = remember(records, offset) { Stats.inWeek(records, anchor) }
    val totalMinutes = minutes.sumOf { it.second }
    RepwiseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val start = Stats.weekStart(anchor)
            Navigator(
                label = if (offset == 0L) "This week" else "Week of ${start.format(DateTimeFormatter.ofPattern("MMM d"))}",
                onPrevious = { offset-- }, onNext = { offset++ }, nextEnabled = offset < 0,
            )
            Spacer(Modifier.height(16.dp))
            WeekBars(minutes, settings)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(week.size / profile.weeklyGoal.toFloat(), size = 64.dp, stroke = 7.dp) {
                    Text("${week.size}/${profile.weeklyGoal}", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Weekly goal", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (week.size >= profile.weeklyGoal) "Goal reached — great consistency!" else "${profile.weeklyGoal - week.size} more to hit your goal",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$totalMinutes / ${profile.difficulty.weeklyMinutesGoal} active minutes",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearMeter(totalMinutes / profile.difficulty.weeklyMinutesGoal.toFloat(), height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun WeekBars(minutes: List<Pair<LocalDate, Int>>, settings: AppSettings) {
    val max = (minutes.maxOfOrNull { it.second } ?: 0).coerceAtLeast(10)
    val grow = remember(minutes) { Animatable(0f) }
    LaunchedEffect(minutes) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val primary = MaterialTheme.colorScheme.primary
    val glow = LocalRepwiseColors.current.glow
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        minutes.forEach { (day, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (value > 0) "$value" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Canvas(Modifier.width(22.dp).height(100.dp)) {
                    drawRoundRect(track, cornerRadius = CornerRadius(8.dp.toPx()))
                    val h = size.height * (value / max.toFloat()) * grow.value
                    if (h > 0f) {
                        drawRoundRect(
                            Brush.verticalGradient(listOf(glow, primary), startY = size.height - h, endY = size.height),
                            topLeft = Offset(0f, size.height - h), size = Size(size.width, h),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    day.dayOfWeek.shortLabel().take(1),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape)
                        .background(if (day.dayOfWeek in settings.planDays) MaterialTheme.colorScheme.primary else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun MonthView(records: List<WorkoutRecord>, settings: AppSettings) {
    var monthOffset by rememberSaveable { mutableLongStateOf(0L) }
    val month = YearMonth.now().plusMonths(monthOffset)
    val counts = remember(records, monthOffset) { Stats.monthCounts(records, month) }
    val primary = MaterialTheme.colorScheme.primary
    val today = LocalDate.now()
    RepwiseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Navigator(
                label = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                onPrevious = { monthOffset-- }, onNext = { monthOffset++ }, nextEnabled = monthOffset < 0,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                java.time.DayOfWeek.entries.forEach {
                    Text(it.shortLabel().take(1), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            val leading = month.atDay(1).dayOfWeek.value - 1
            val cells = leading + month.lengthOfMonth()
            val rows = (cells + 6) / 7
            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNumber = row * 7 + col - leading + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (dayNumber in 1..month.lengthOfMonth()) {
                                val date = month.atDay(dayNumber)
                                val count = counts[date] ?: 0
                                val planned = date.dayOfWeek in settings.planDays && !date.isBefore(today)
                                Box(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                        .background(if (count > 0) primary.copy(alpha = (0.35f + count * 0.2f).coerceAtMost(1f)) else MaterialTheme.colorScheme.surfaceContainer)
                                        .then(if (planned && count == 0) Modifier.border(1.5.dp, primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)) else Modifier)
                                        .then(if (date == today) Modifier.border(2.dp, primary, RoundedCornerShape(10.dp)) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$dayNumber", style = MaterialTheme.typography.labelMedium,
                                        color = if (count > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("${counts.values.sum()} sessions")
                Pill("${counts.size} active days", container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(6.dp))
            Text("Outlined days are planned training days.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Navigator(label: String, onPrevious: () -> Unit, onNext: () -> Unit, nextEnabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = onNext, enabled = nextEnabled) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label.uppercase(), style = MaterialTheme.typography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InsightsCard(records: List<WorkoutRecord>) {
    val byExercise = remember(records) { Stats.repsByExercise(records).take(5) }
    if (byExercise.isEmpty()) return
    val max = byExercise.maxOf { it.second }.coerceAtLeast(1)
    RepwiseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Top moves", style = MaterialTheme.typography.titleMedium)
            byExercise.forEach { (name, total) ->
                Column {
                    Row {
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearMeter(total / max.toFloat(), height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun BadgeGrid(earned: Set<Badge>) {
    val columns = 3
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Badge.entries.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { colIndex, badge ->
                    BadgeTile(badge, badge in earned, Modifier.weight(1f).staggeredEntrance(rowIndex * columns + colIndex))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, unlocked: Boolean, modifier: Modifier) {
    var showInfo by remember { mutableStateOf(false) }
    val colors = LocalRepwiseColors.current
    RepwiseCard(modifier, onClick = { showInfo = true }) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(if (unlocked) colors.heroBrush else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (unlocked) badge.icon() else Icons.Default.Lock, null,
                    tint = if (unlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).graphicsLayer { alpha = if (unlocked) 1f else 0.6f },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                badge.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            icon = { Icon(badge.icon(), null) },
            title = { Text(badge.title) },
            text = { Text(if (unlocked) "Unlocked — ${badge.description.replaceFirstChar { it.lowercase() }}." else "Locked. ${badge.description}.") },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun HistoryRow(record: WorkoutRecord, modifier: Modifier, onDelete: () -> Unit) {
    RepwiseCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(record.type?.icon() ?: Icons.Default.Timer, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.exercise, style = MaterialTheme.typography.titleSmall)
                    if (record.targetHit) {
                        Spacer(Modifier.width(6.dp))
                        Pill("Target")
                    }
                }
                Text(
                    "${record.movementLabel()} · ${formatDuration(record.activeSeconds)} active · ${record.calories} kcal",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(record.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete session", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
