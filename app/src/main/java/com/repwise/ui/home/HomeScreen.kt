package com.repwise.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repwise.domain.Coach
import com.repwise.domain.ExerciseType
import com.repwise.domain.Gamification
import com.repwise.domain.Stats
import com.repwise.reminders.shortLabel
import com.repwise.ui.AppViewModel
import com.repwise.ui.Tab
import com.repwise.ui.components.AnimatedNumber
import com.repwise.ui.components.IconBubble
import com.repwise.ui.components.LinearMeter
import com.repwise.ui.components.Pill
import com.repwise.ui.components.ProgressRing
import com.repwise.ui.components.RepwiseCard
import com.repwise.ui.components.SectionHeader
import com.repwise.ui.components.StatTile
import com.repwise.ui.components.GradientHero
import com.repwise.ui.components.formatDuration
import com.repwise.ui.components.icon
import com.repwise.ui.components.staggeredEntrance
import com.repwise.ui.theme.LocalRepwiseColors
import com.repwise.ui.theme.overline
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(vm: AppViewModel, onExercise: (ExerciseType) -> Unit) {
    val records by vm.records.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val week = remember(records) { Stats.inWeek(records, today) }
    val streak = remember(records) { Stats.streak(records, today) }
    val score = remember(records, profile) { Stats.fitnessScore(records, profile, today) }
    val suggestions = remember(records, profile.difficulty) { Coach.suggestions(records, profile.difficulty, today) }
    val usualHour = remember(records) { Stats.usualHour(records) }
    val trainedToday = remember(records) { Stats.onDay(records, today).isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 24.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().staggeredEntrance(0), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting().uppercase(), style = MaterialTheme.typography.overline, color = MaterialTheme.colorScheme.primary)
                    Text(profile.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LevelChip(game.level.level, game.level.title, game.level.progress, onClick = { vm.selectTab(Tab.PROGRESS) })
            }
        }
        item {
            GradientHero(Modifier.fillMaxWidth().staggeredEntrance(1)) {
                Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FITNESS SCORE", style = MaterialTheme.typography.overline, color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (trainedToday) "Nice work today.\nKeep it rolling." else "Move better.\nFeel stronger.",
                            style = MaterialTheme.typography.headlineSmall, color = Color.White,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalFireDepartment, null, tint = LocalRepwiseColors.current.glow, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (streak > 0) "$streak-day streak" else "Start a streak today", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { vm.selectTab(Tab.TRAIN) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = LocalRepwiseColors.current.heroStart),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text("Start a workout")
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                        }
                    }
                    ProgressRing(
                        progress = score / 100f, size = 108.dp, stroke = 11.dp,
                        track = Color.White.copy(alpha = 0.15f),
                        brush = Brush.sweepGradient(listOf(LocalRepwiseColors.current.glow, Color.White, LocalRepwiseColors.current.glow)),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNumber(score, MaterialTheme.typography.headlineMedium, color = Color.White)
                            Text("/ 100", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
        item {
            ChallengeCard(
                title = game.challenge.title,
                progress = game.challengeProgress,
                goal = game.challenge.goal,
                modifier = Modifier.staggeredEntrance(2),
                onClick = { game.challenge.exercise?.let(onExercise) ?: vm.selectTab(Tab.TRAIN) },
            )
        }
        item {
            Row(Modifier.staggeredEntrance(3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("THIS WEEK", week.size, "of ${profile.weeklyGoal} workouts", Modifier.weight(1f))
                StatTile("ACTIVE", week.sumOf { it.activeSeconds } / 60, "minutes", Modifier.weight(1f))
                StatTile("CALORIES", week.sumOf { it.calories }, "kcal this week", Modifier.weight(1f))
            }
        }
        item {
            ScheduleHint(
                trainedToday = trainedToday,
                plannedToday = today.dayOfWeek in settings.planDays,
                nextDay = (1L..7L).map { today.plusDays(it) }.firstOrNull { it.dayOfWeek in settings.planDays }?.dayOfWeek?.shortLabel(),
                usualHour = usualHour,
                modifier = Modifier.staggeredEntrance(4),
            )
        }
        item { SectionHeader("Coach picks", Modifier.staggeredEntrance(5), action = "All moves", onAction = { vm.selectTab(Tab.TRAIN) }) }
        itemsIndexed(suggestions, key = { _, s -> s.exercise.name }) { index, suggestion ->
            RepwiseCard(Modifier.fillMaxWidth().staggeredEntrance(6 + index), onClick = { onExercise(suggestion.exercise) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(suggestion.exercise.icon())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(suggestion.exercise.title, style = MaterialTheme.typography.titleMedium)
                        Text(suggestion.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Pill("${suggestion.exercise.target(profile.difficulty)}${if (suggestion.exercise.isTimed) "s" else ""}")
                }
            }
        }
        item {
            Row(Modifier.staggeredEntrance(9), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Interval circuit", "${settings.interval.rounds} × ${settings.interval.workSeconds}s", Icons.Default.Timer, Modifier.weight(1f)) {
                    vm.selectTab(Tab.TRAIN)
                }
                QuickAction("Focus sounds", "Music & ambience", Icons.Default.GraphicEq, Modifier.weight(1f)) { vm.selectTab(Tab.SOUNDS) }
            }
        }
        if (records.isNotEmpty()) {
            item { SectionHeader("Recent", action = "History", onAction = { vm.selectTab(Tab.PROGRESS) }) }
            itemsIndexed(records.take(3), key = { _, r -> r.id }) { _, record ->
                RepwiseCard(Modifier.fillMaxWidth().animateItem()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(record.type?.icon() ?: Icons.Default.Timer, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.exercise, style = MaterialTheme.typography.titleSmall)
                            Text("${record.movementLabel()} · ${formatDuration(record.activeSeconds)} active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("+${Gamification.xpFor(record)} XP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hey"
}

@Composable
private fun LevelChip(level: Int, title: String, progress: Float, onClick: () -> Unit) {
    RepwiseCard(onClick = onClick) {
        Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress, size = 38.dp, stroke = 4.dp) {
                Text("$level", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("LEVEL $level", style = MaterialTheme.typography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ChallengeCard(title: String, progress: Int, goal: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val done = progress >= goal
    RepwiseCard(modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(if (done) Icons.Default.CheckCircle else Icons.Default.EmojiEvents, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("DAILY CHALLENGE", style = MaterialTheme.typography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Pill(if (done) "Done" else "+${Gamification.CHALLENGE_XP} XP")
            }
            Spacer(Modifier.height(12.dp))
            LinearMeter(progress / goal.toFloat().coerceAtLeast(1f), height = 8.dp)
            Spacer(Modifier.height(6.dp))
            AnimatedVisibility(!done) {
                Text("$progress of $goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScheduleHint(trainedToday: Boolean, plannedToday: Boolean, nextDay: String?, usualHour: Int?, modifier: Modifier = Modifier) {
    val (icon, text) = when {
        trainedToday -> Icons.Default.CheckCircle to "Today's training is logged. Recovery matters too — see you ${nextDay ?: "soon"}."
        plannedToday && usualHour != null -> Icons.Default.Schedule to "Today is a planned training day. You usually train around ${formatHour(usualHour)}."
        plannedToday -> Icons.Default.CalendarMonth to "Today is a planned training day. Even 10 minutes counts."
        usualHour != null -> Icons.Default.TipsAndUpdates to "Rest day on your plan. Next session: ${nextDay ?: "set one in Profile"}, around ${formatHour(usualHour)}."
        else -> Icons.Default.TipsAndUpdates to "Rest day on your plan. Next session: ${nextDay ?: "set your plan in Profile"}."
    }
    RepwiseCard(modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

fun formatHour(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

@Composable
private fun QuickAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    RepwiseCard(modifier, onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            IconBubble(icon, size = 38.dp)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
