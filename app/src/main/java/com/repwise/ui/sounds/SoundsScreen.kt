package com.repwise.ui.sounds

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tsunami
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repwise.audio.AmbientSound
import com.repwise.audio.MusicTrack
import com.repwise.audio.PlayerState
import com.repwise.audio.SoundCategory
import com.repwise.ui.AppViewModel
import com.repwise.ui.components.GradientHero
import com.repwise.ui.components.IconBubble
import com.repwise.ui.components.Pill
import com.repwise.ui.components.RepwiseCard
import com.repwise.ui.components.SectionHeader
import com.repwise.ui.components.staggeredEntrance
import com.repwise.ui.theme.LocalRepwiseColors
import com.repwise.ui.theme.overline
import java.time.LocalTime

@Composable
fun SoundsScreen(vm: AppViewModel) {
    val player by vm.audio.state.collectAsStateWithLifecycle()
    val mixes by vm.mixes.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf<SoundCategory?>(null) }
    var naming by remember { mutableStateOf(false) }
    val recommended = remember(records) {
        records.firstOrNull()?.type?.let(MusicTrack::recommendedFor) ?: when (LocalTime.now().hour) {
            in 5..11 -> MusicTrack.POWER_UP
            in 12..17 -> MusicTrack.STEADY_PACE
            else -> MusicTrack.LOFI_COOLDOWN
        }
    }
    val reason = if (records.isNotEmpty()) "Matches your last workout" else "Picked for this time of day"
    val tracks = MusicTrack.entries.filter { category == null || it.category == category }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 24.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("Sounds", style = MaterialTheme.typography.headlineMedium)
                Text("Generated on-device — works offline and keeps playing in the background.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { NowPlaying(player, vm, Modifier.staggeredEntrance(0)) }
        item {
            RepwiseCard(Modifier.fillMaxWidth().staggeredEntrance(1), onClick = { vm.audio.playTrack(recommended) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Default.LibraryMusic)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("RECOMMENDED", style = MaterialTheme.typography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(recommended.title, style = MaterialTheme.typography.titleMedium)
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.PlayArrow, "Play ${recommended.title}", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { SectionHeader("Music") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") }) }
                items(SoundCategory.entries.filter { it != SoundCategory.NATURE }) { c ->
                    FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c.label) })
                }
            }
        }
        items(tracks, key = { it.name }) { track ->
            TrackRow(
                track = track,
                selected = player.track == track,
                playing = player.playing && player.track == track,
                loading = player.loadingTrack && player.track == track,
                modifier = Modifier.animateItem(),
                onClick = {
                    if (player.track == track) vm.audio.togglePlay() else vm.audio.playTrack(track)
                },
            )
        }
        item { SectionHeader("Ambient mixer") }
        item {
            Text(
                "Layer nature sounds and noise under your music. Each layer has its own volume.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(AmbientSound.entries, key = { it.name }) { sound ->
            AmbientRow(
                sound = sound,
                volume = player.layers[sound],
                onToggle = { vm.audio.toggleLayer(sound) },
                onVolume = { vm.audio.setLayerVolume(sound, it) },
            )
        }
        item {
            SectionHeader("Saved mixes", action = "Save current", onAction = { naming = true })
        }
        if (mixes.isEmpty()) {
            item {
                Text(
                    "Build a combination you like, then save it here for one-tap playback.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(mixes, key = { "mix-" + it.name }) { mix ->
            RepwiseCard(Modifier.fillMaxWidth().animateItem(), onClick = { vm.audio.applyMix(mix) }) {
                Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Default.GraphicEq, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mix.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(mix.track?.title, mix.layers.keys.joinToString { it.title }.ifBlank { null }).joinToString(" + "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.deleteMix(mix) }) { Icon(Icons.Default.Close, "Delete ${mix.name}") }
                }
            }
        }
        item { SpotifyCard() }
    }

    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            icon = { Icon(Icons.Default.Save, null) },
            title = { Text("Save this mix") },
            text = { OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.saveMix(name); naming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NowPlaying(player: PlayerState, vm: AppViewModel, modifier: Modifier) {
    GradientHero(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (player.playing) "NOW PLAYING" else "READY", style = MaterialTheme.typography.overline, color = Color.White.copy(alpha = 0.7f))
                    Text(player.label, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2)
                    player.error?.let { Text(it, color = Color(0xFFFFC7B5), style = MaterialTheme.typography.bodySmall) }
                }
                Equalizer(player.playing, Modifier.size(width = 40.dp, height = 32.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = { if (player.hasContent) vm.audio.togglePlay() else vm.audio.playTrack(MusicTrack.STEADY_PACE) },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = LocalRepwiseColors.current.heroStart),
                ) {
                    Icon(if (player.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (player.playing) "Pause" else "Play", modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = vm.audio::stop, enabled = player.hasContent) {
                    Icon(Icons.Default.Stop, "Stop", tint = Color.White.copy(alpha = if (player.hasContent) 1f else 0.4f))
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                Slider(
                    value = player.musicVolume,
                    onValueChange = vm.audio::setMusicVolume,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun Equalizer(active: Boolean, modifier: Modifier) {
    val color = LocalRepwiseColors.current.glow
    if (!active) {
        // No infinite animation while idle, so the screen stops redrawing when nothing plays.
        EqualizerBars(List(4) { 0.15f }, color, modifier)
        return
    }
    val transition = rememberInfiniteTransition(label = "eq")
    val bars = List(4) { i ->
        transition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(380 + i * 110), RepeatMode.Reverse), label = "bar$i")
    }
    Canvas(modifier) { drawBars(bars.map { it.value }, color) }
}

@Composable
private fun EqualizerBars(levels: List<Float>, color: Color, modifier: Modifier) = Canvas(modifier) { drawBars(levels, color) }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBars(levels: List<Float>, color: Color) {
    val w = size.width / 7
    levels.forEachIndexed { i, level ->
        val h = size.height * level
        drawRoundRect(color, Offset(i * w * 2, size.height - h), Size(w, h), CornerRadius(w / 2))
    }
}

@Composable
private fun TrackRow(track: MusicTrack, selected: Boolean, playing: Boolean, loading: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, label = "track",
    )
    RepwiseCard(modifier.fillMaxWidth(), onClick = onClick, containerColor = container) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(if (playing) Icons.Default.Pause else Icons.Default.MusicNote, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall)
                Text(if (loading) "Preparing…" else track.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Pill(track.category.label)
                Text("${track.bpm} BPM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun AmbientSound.icon(): ImageVector = when (this) {
    AmbientSound.RAIN -> Icons.Default.Umbrella
    AmbientSound.OCEAN -> Icons.Default.Tsunami
    AmbientSound.FOREST -> Icons.Default.Forest
    AmbientSound.FIREPLACE -> Icons.Default.LocalFireDepartment
    AmbientSound.CAFE -> Icons.Default.Coffee
    AmbientSound.WHITE_NOISE -> Icons.Default.Air
    AmbientSound.BROWN_NOISE -> Icons.Default.Waves
}

@Composable
private fun AmbientRow(sound: AmbientSound, volume: Float?, onToggle: () -> Unit, onVolume: (Float) -> Unit) {
    val on = volume != null
    val container by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, label = "ambient",
    )
    RepwiseCard(Modifier.fillMaxWidth(), onClick = onToggle, containerColor = container) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(sound.icon(), size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Text(sound.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(if (on) "On" else "Off", style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(on, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Slider(value = volume ?: 0f, onValueChange = onVolume, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun SpotifyCard() {
    val context = LocalContext.current
    RepwiseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Prefer your own music?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Open Spotify to a workout search. Repwise sounds pause automatically when another app takes over audio.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                val app = Intent(Intent.ACTION_VIEW, "spotify:search:workout".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val web = Intent(Intent.ACTION_VIEW, "https://open.spotify.com/search/workout".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(app)
                } catch (_: ActivityNotFoundException) {
                    runCatching { context.startActivity(web) }
                }
            }) { Text("Open Spotify") }
        }
    }
}
