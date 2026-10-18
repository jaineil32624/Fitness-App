package com.repwise.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repwise.audio.MusicTrack
import com.repwise.domain.SessionReward
import com.repwise.ui.AppViewModel
import com.repwise.ui.components.Confetti
import com.repwise.ui.components.GlassPanel
import com.repwise.ui.components.LinearMeter
import com.repwise.ui.components.PulseRings
import com.repwise.ui.components.formatDuration
import com.repwise.ui.components.icon
import com.repwise.ui.theme.LocalRepwiseColors
import com.repwise.ui.theme.overline
import kotlinx.coroutines.delay

@Composable
fun SummaryScreen(reward: SessionReward, vm: AppViewModel) {
    val colors = LocalRepwiseColors.current
    val record = reward.record
    val xp = remember { Animatable(0f) }
    var levelProgress by remember { mutableFloatStateOf(reward.before.progress) }
    var showLevelUp by remember { mutableStateOf(false) }
    var revealed by remember { mutableIntStateOf(0) }

    BackHandler(onBack = vm::closeSummary)

    LaunchedEffect(reward) {
        delay(350)
        xp.animateTo(reward.xpEarned.toFloat(), tween(1_100, easing = FastOutSlowInEasing))
        if (reward.leveledUp) {
            levelProgress = 1f
            delay(700)
            showLevelUp = true
        }
        levelProgress = reward.after.progress
        for (i in 1..(reward.newBadges.size + reward.newThemes.size + 1)) {
            delay(260)
            revealed = i
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.heroEnd, colors.heroStart, Color.Black)))) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())
                .padding(24.dp).align(Alignment.TopCenter).widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(contentAlignment = Alignment.Center) {
                PulseRings(colors.glow, Modifier.size(180.dp))
                Icon(Icons.Default.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(72.dp))
            }
            Text(if (reward.leveledUp) "Level up!" else "Workout complete", color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Text(
                "${record.exercise} · ${record.movementLabel()} · ${formatDuration(record.activeSeconds)} active",
                color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("XP EARNED", style = MaterialTheme.typography.overline, color = Color.White.copy(alpha = 0.7f))
                    Text("+${xp.value.toInt()}", color = colors.glow, fontSize = 52.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Level ${if (showLevelUp) reward.after.level else reward.before.level} · ${if (showLevelUp) reward.after.title else reward.before.title}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Text("${reward.after.xpIntoLevel} / ${reward.after.xpForNextLevel}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearMeter(levelProgress, height = 10.dp, brush = Brush.horizontalGradient(listOf(colors.glow, Color.White)))
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SummaryStat("KCAL", "${record.calories}")
                        SummaryStat("ELAPSED", formatDuration(record.seconds))
                        SummaryStat("TARGET", if (record.targetHit) "Hit" else "—")
                    }
                }
            }

            if (reward.challengeCompleted) {
                Spacer(Modifier.height(12.dp))
                RewardRow(visible = revealed >= 1, icon = Icons.Default.EmojiEvents, title = "Daily challenge complete", subtitle = "Bonus XP added")
            }
            reward.newBadges.forEachIndexed { index, badge ->
                Spacer(Modifier.height(12.dp))
                RewardRow(visible = revealed >= index + 1, icon = badge.icon(), title = "Badge unlocked: ${badge.title}", subtitle = badge.description)
            }
            reward.newThemes.forEachIndexed { index, theme ->
                Spacer(Modifier.height(12.dp))
                RewardRow(visible = revealed >= reward.newBadges.size + index + 1, icon = Icons.Default.Palette, title = "Theme unlocked: ${theme.label}", subtitle = "Apply it in Profile → Appearance")
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Cool down with ${MusicTrack.CALM_HORIZON.title}?",
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.audio.playTrack(MusicTrack.CALM_HORIZON) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White),
                ) { Text("Play cooldown") }
                Button(
                    onClick = vm::closeSummary,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = colors.heroStart),
                ) { Text("Done", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(24.dp))
        }
        Confetti(Modifier.fillMaxSize(), key = reward, colors = listOf(colors.glow, Color.White, Color(0xFFFFD66B), colors.heroEnd))
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.overline, color = Color.White.copy(alpha = 0.65f))
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun RewardRow(visible: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.7f) + fadeIn() + slideInVertically { it / 2 },
    ) {
        GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), tint = LocalRepwiseColors.current.heroEnd) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
