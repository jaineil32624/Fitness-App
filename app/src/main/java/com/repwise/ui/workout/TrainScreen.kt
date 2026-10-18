package com.repwise.ui.workout

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repwise.domain.ExerciseType
import com.repwise.domain.IntervalConfig
import com.repwise.domain.MuscleGroup
import com.repwise.domain.Stats
import com.repwise.ui.AppViewModel
import com.repwise.ui.components.GradientHero
import com.repwise.ui.components.IconBubble
import com.repwise.ui.components.Pill
import com.repwise.ui.components.RepwiseCard
import com.repwise.ui.components.formatDuration
import com.repwise.ui.components.icon
import com.repwise.ui.components.staggeredEntrance
import com.repwise.ui.theme.LocalRepwiseColors
import com.repwise.ui.theme.overline

@Composable
fun TrainScreen(vm: AppViewModel, onExercise: (ExerciseType) -> Unit) {
    val records by vm.records.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    val bests = remember(records) { Stats.personalBests(records) }
    val exercises = ExerciseType.entries.filter { filter == null || it.group == filter }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 24.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Train", style = MaterialTheme.typography.headlineMedium)
                Text("Pose tracking runs entirely on your device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            IntervalCard(settings.interval, onStart = vm::startInterval)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") }) }
                items(MuscleGroup.entries) { group ->
                    FilterChip(selected = filter == group, onClick = { filter = if (filter == group) null else group }, label = { Text(group.label) })
                }
            }
        }
        itemsIndexed(exercises, key = { _, e -> e.name }) { index, exercise ->
            RepwiseCard(Modifier.fillMaxWidth().staggeredEntrance(index).animateItem(), onClick = { onExercise(exercise) }) {
                Column(Modifier.padding(14.dp)) {
                    IconBubble(exercise.icon())
                    Spacer(Modifier.height(12.dp))
                    Text(exercise.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(exercise.group.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    val unit = if (exercise.isTimed) "s" else ""
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("Goal ${exercise.target(profile.difficulty)}$unit")
                        bests[exercise]?.let {
                            Pill("Best $it$unit", container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            RepwiseCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Prop your phone a few feet away in a bright space and keep your whole body in frame. You can switch between front and rear cameras during a workout.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntervalCard(saved: IntervalConfig, onStart: (IntervalConfig) -> Unit) {
    var work by rememberSaveable(saved) { mutableIntStateOf(saved.workSeconds) }
    var rest by rememberSaveable(saved) { mutableIntStateOf(saved.restSeconds) }
    var rounds by rememberSaveable(saved) { mutableIntStateOf(saved.rounds) }
    val total = (work + rest) * rounds - rest
    GradientHero(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, tint = LocalRepwiseColors.current.glow)
                Spacer(Modifier.width(8.dp))
                Text("INTERVAL CIRCUIT", style = MaterialTheme.typography.overline, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                Text(formatDuration(total.coerceAtLeast(0)), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            Spacer(Modifier.height(4.dp))
            Text("Timed work and rest rounds with audio cues — no camera needed.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Stepper("Work", "${work}s", onMinus = { work = (work - 5).coerceAtLeast(5) }, onPlus = { work = (work + 5).coerceAtMost(600) })
                Stepper("Rest", "${rest}s", onMinus = { rest = (rest - 5).coerceAtLeast(0) }, onPlus = { rest = (rest + 5).coerceAtMost(600) })
                Stepper("Rounds", "$rounds", onMinus = { rounds = (rounds - 1).coerceAtLeast(1) }, onPlus = { rounds = (rounds + 1).coerceAtMost(50) })
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onStart(IntervalConfig(work, rest, rounds)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = LocalRepwiseColors.current.heroStart),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Start circuit")
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.overline, color = Color.White.copy(alpha = 0.7f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White,
            )
            FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(32.dp), colors = colors) {
                Icon(Icons.Default.Remove, "Decrease $label", modifier = Modifier.size(16.dp))
            }
            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp))
            FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(32.dp), colors = colors) {
                Icon(Icons.Default.Add, "Increase $label", modifier = Modifier.size(16.dp))
            }
        }
    }
}
