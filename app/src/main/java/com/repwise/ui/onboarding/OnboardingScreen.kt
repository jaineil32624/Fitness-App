package com.repwise.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.repwise.domain.Difficulty
import com.repwise.domain.UserProfile
import com.repwise.ui.components.PulseRings
import com.repwise.ui.theme.LocalRepwiseColors
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class Page(val icon: ImageVector, val title: String, val body: String, val points: List<Pair<ImageVector, String>>)

private val pages = listOf(
    Page(
        Icons.Default.FitnessCenter, "Your camera is\nyour coach",
        "Repwise watches your form, counts every rep, and tracks active time — no wearables needed.",
        listOf(Icons.Default.CameraAlt to "10 exercises with live form cues", Icons.Default.LocalFireDepartment to "Streaks, goals, and a weekly fitness score"),
    ),
    Page(
        Icons.Default.Lock, "Private by design",
        "Pose detection runs entirely on your phone. Video is never recorded, stored, or uploaded.",
        listOf(Icons.Default.Visibility to "Frames are analyzed and discarded instantly", Icons.Default.Lock to "History stays in app-private storage"),
    ),
    Page(
        Icons.Default.EmojiEvents, "Level up as\nyou move",
        "Earn XP for every session, finish daily challenges, unlock badges and new themes — with music to keep you going.",
        listOf(Icons.Default.EmojiEvents to "18 badges and 5 unlockable themes", Icons.Default.GraphicEq to "Offline workout music and ambient mixer"),
    ),
)

@Composable
fun OnboardingScreen(onFinish: (UserProfile, Boolean) -> Unit) {
    val pager = rememberPagerState { pages.size + 1 }
    val scope = rememberCoroutineScope()
    val colors = LocalRepwiseColors.current
    var name by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("68") }
    var goal by rememberSaveable { mutableStateOf("4") }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.BEGINNER) }
    var reminders by rememberSaveable { mutableStateOf(true) }

    fun finish(remindersAllowed: Boolean) {
        onFinish(
            UserProfile(
                name = name.trim().ifBlank { "Athlete" },
                weightKg = weight.toFloatOrNull()?.coerceIn(25f, 250f) ?: 68f,
                weeklyGoal = goal.toIntOrNull()?.coerceIn(1, 14) ?: 4,
                difficulty = difficulty,
            ),
            remindersAllowed,
        )
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> finish(granted) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.heroEnd, colors.heroStart)))) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                if (pager.currentPage < pages.size) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pages.size) } }) { Text("Skip", color = Color.White) }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
            HorizontalPager(pager, modifier = Modifier.weight(1f)) { index ->
                // Parallax: content drifts and fades as pages slide.
                val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = 1f - offset * 0.6f; translationX = offset * 60f },
                    contentAlignment = Alignment.Center,
                ) {
                    if (index < pages.size) IntroPage(pages[index])
                    else SetupPage(name, { name = it }, weight, { weight = it }, goal, { goal = it }, difficulty, { difficulty = it }, reminders, { reminders = it })
                }
            }
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    repeat(pages.size + 1) { i -> Dot(selected = pager.currentPage == i) }
                }
                val last = pager.currentPage == pages.size
                Button(
                    onClick = {
                        if (!last) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        else if (reminders && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else finish(reminders)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = colors.heroStart),
                    modifier = Modifier.height(52.dp),
                ) { Text(if (last) "Get started" else "Next", style = MaterialTheme.typography.titleSmall) }
            }
        }
    }
}

@Composable
private fun Dot(selected: Boolean) {
    val width by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dot")
    val color by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.35f), label = "dotColor")
    Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
}

@Composable
private fun IntroPage(page: Page) {
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(-8f, 8f, infiniteRepeatable(tween(1800), androidx.compose.animation.core.RepeatMode.Reverse), label = "bob")
    Column(
        Modifier.widthIn(max = 480.dp).padding(horizontal = 32.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            PulseRings(Color.White, Modifier.fillMaxSize())
            Box(
                Modifier.size(110.dp).graphicsLayer { translationY = bob }.clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.1f)))),
                contentAlignment = Alignment.Center,
            ) { Icon(page.icon, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
        }
        Spacer(Modifier.height(24.dp))
        Text(page.title, style = MaterialTheme.typography.headlineLarge, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(page.body, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        page.points.forEach { (icon, text) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.1f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = LocalRepwiseColors.current.glow, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SetupPage(
    name: String, onName: (String) -> Unit,
    weight: String, onWeight: (String) -> Unit,
    goal: String, onGoal: (String) -> Unit,
    difficulty: Difficulty, onDifficulty: (Difficulty) -> Unit,
    reminders: Boolean, onReminders: (Boolean) -> Unit,
) {
    Column(
        Modifier.widthIn(max = 480.dp).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier.clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Set up your profile", style = MaterialTheme.typography.titleLarge)
            }
            Text("Used for rep targets and calorie estimates. You can change it anytime.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { onName(it.take(30)) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    weight, { onWeight(it.filter(Char::isDigit).take(3)) }, label = { Text("Weight (kg)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    goal, { onGoal(it.filter(Char::isDigit).take(2)) }, label = { Text("Workouts / week") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
            }
            Text("Experience", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Difficulty.entries.forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = difficulty == level, onClick = { onDifficulty(level) },
                        shape = SegmentedButtonDefaults.itemShape(index, Difficulty.entries.size),
                    ) { Text(level.label, maxLines = 1) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Workout reminders", style = MaterialTheme.typography.titleSmall)
                    Text("A nudge on your training days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(reminders, onReminders)
            }
        }
    }
}
