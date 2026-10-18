package com.repwise.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repwise.ui.AppViewModel
import com.repwise.ui.IntervalPhase
import com.repwise.ui.SessionEvent
import com.repwise.ui.components.ProgressRing
import com.repwise.ui.components.PulseRings
import com.repwise.ui.components.formatDuration
import com.repwise.ui.components.rememberHaptics
import com.repwise.ui.theme.LocalRepwiseColors

@Composable
fun IntervalScreen(vm: AppViewModel) {
    val session by vm.interval.collectAsStateWithLifecycle()
    val s = session ?: return
    val settings by vm.settings.collectAsStateWithLifecycle()
    val haptics = rememberHaptics(settings.haptics)
    var confirmEnd by remember { mutableStateOf(false) }
    val colors = LocalRepwiseColors.current

    val phaseColor by animateColorAsState(
        when (s.phase) {
            IntervalPhase.PREP -> Color(0xFF3B4A55)
            IntervalPhase.WORK -> colors.heroEnd
            IntervalPhase.REST -> Color(0xFF2F5D7C)
            IntervalPhase.DONE -> colors.heroEnd
        },
        tween(600), label = "phase",
    )

    BackHandler { if (s.phase == IntervalPhase.DONE) vm.finishInterval(save = true) else confirmEnd = true }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) { vm.events.collect { if (it == SessionEvent.PhaseChange) haptics.heavy() } }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(phaseColor, colors.heroStart))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp).widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (s.phase == IntervalPhase.DONE) vm.finishInterval(true) else confirmEnd = true }) {
                    Icon(Icons.Default.Close, "End circuit", tint = Color.White)
                }
                Text("Interval circuit", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(formatDuration((s.elapsedMs / 1000).toInt()), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = s.phase,
                transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.8f)) togetherWith fadeOut(tween(150)) },
                label = "phaseLabel",
            ) { phase ->
                Text(
                    when (phase) {
                        IntervalPhase.PREP -> "GET READY"
                        IntervalPhase.WORK -> "WORK"
                        IntervalPhase.REST -> "REST"
                        IntervalPhase.DONE -> "CIRCUIT COMPLETE"
                    },
                    color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            Box(contentAlignment = Alignment.Center) {
                if (s.phase == IntervalPhase.WORK && !s.paused) PulseRings(Color.White, Modifier.size(300.dp))
                ProgressRing(
                    progress = if (s.phase == IntervalPhase.DONE) 1f else 1f - s.phaseRemainingMs / s.phaseTotalMs.toFloat(),
                    size = 250.dp, stroke = 16.dp, color = Color.White, track = Color.White.copy(alpha = 0.15f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (s.phase == IntervalPhase.DONE) "${s.roundsCompleted}" else "${((s.phaseRemainingMs + 999) / 1000)}",
                            color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (s.phase == IntervalPhase.DONE) "rounds done" else "Round ${s.round} of ${s.config.rounds}",
                            color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (s.phase == IntervalPhase.DONE) {
                Button(
                    onClick = { vm.finishInterval(save = true) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = colors.heroStart),
                ) { Text("Save & see rewards", fontWeight = FontWeight.Bold) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Work ${formatDuration((s.workMs / 1000).toInt())}", color = Color.White.copy(alpha = 0.8f))
                    FilledIconButton(
                        onClick = vm::toggleIntervalPause,
                        modifier = Modifier.size(76.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = colors.heroStart),
                    ) {
                        Icon(if (s.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (s.paused) "Resume" else "Pause", modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = vm::skipIntervalPhase) { Icon(Icons.Default.SkipNext, "Skip phase", tint = Color.White) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End circuit?") },
            text = { Text("${s.roundsCompleted} of ${s.config.rounds} rounds done. Save what you've finished, or discard it.") },
            confirmButton = { Button(onClick = { confirmEnd = false; vm.finishInterval(save = true) }) { Text("Save") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmEnd = false; vm.finishInterval(save = false) }) { Text("Discard") }
                    TextButton(onClick = { confirmEnd = false }) { Text("Keep going") }
                }
            },
        )
    }
}
