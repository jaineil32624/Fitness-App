package com.repwise.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AirlineSeatFlat
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repwise.domain.Badge
import com.repwise.domain.ExerciseType
import com.repwise.ui.theme.LocalRepwiseColors
import com.repwise.ui.theme.overline
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

fun formatDuration(seconds: Int): String =
    if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)

/** Shrinks slightly while pressed — a tactile cue for tappable cards and buttons. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    pressScale(source).clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick)
}

/** Fades and slides content up once when it first appears, delayed by its position in a list. */
fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 420, delayMillis = (index * 55).coerceAtMost(440), easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}

@Composable
fun RepwiseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = LocalRepwiseColors.current.isDark
    val border = if (dark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    val elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 1.dp)
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    if (onClick != null) {
        val source = remember { MutableInteractionSource() }
        Card(
            onClick = onClick, modifier = modifier.pressScale(source), interactionSource = source,
            shape = MaterialTheme.shapes.large, colors = colors, elevation = elevation, border = border, content = content,
        )
    } else {
        Card(modifier, shape = MaterialTheme.shapes.large, colors = colors, elevation = elevation, border = border, content = content)
    }
}

/** Gradient card with soft decorative orbs, used for the primary call-to-action on a screen. */
@Composable
fun GradientHero(modifier: Modifier = Modifier, brush: Brush = LocalRepwiseColors.current.heroBrush, content: @Composable BoxScope.() -> Unit) {
    val glow = LocalRepwiseColors.current.glow
    Box(
        modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(brush),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(glow.copy(alpha = 0.18f), radius = size.minDimension * 0.55f, center = Offset(size.width * 0.95f, size.height * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.06f), radius = size.minDimension * 0.4f, center = Offset(size.width * 0.8f, size.height * 1.05f))
        }
        content()
    }
}

/** Frosted translucent panel for content drawn over the camera feed. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(20.dp), tint: Color = Color.Black, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.38f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))), shape),
        content = content,
    )
}

@Composable
fun IconBubble(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = MaterialTheme.colorScheme.primary, container: Color = MaterialTheme.colorScheme.primaryContainer) {
    Surface(shape = RoundedCornerShape(size * 0.32f), color = container, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun AnimatedNumber(value: Int, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, suffix: String = "") {
    val animated by animateIntAsState(value, tween(700, easing = FastOutSlowInEasing), label = "number")
    Text("$animated$suffix", style = style, color = color, modifier = modifier, maxLines = 1)
}

@Composable
fun StatTile(label: String, value: Int, unit: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    RepwiseCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(label, style = MaterialTheme.typography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(6.dp))
            AnimatedNumber(value, MaterialTheme.typography.headlineSmall)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    brush: Brush? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 60f), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val strokePx = stroke.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            if (animated > 0f) {
                rotate(-90f) {
                    if (brush != null) drawArc(brush, 0f, 360f * animated, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
                    else drawArc(color, 0f, 360f * animated, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
                }
            }
        }
        content()
    }
}

@Composable
fun LinearMeter(progress: Float, modifier: Modifier = Modifier, height: Dp = 10.dp, brush: Brush? = null) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "meter")
    val fill = brush ?: Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, LocalRepwiseColors.current.glow))
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))) {
        if (animated > 0f) Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(CircleShape).background(fill))
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.primaryContainer, content: Color = MaterialTheme.colorScheme.onPrimaryContainer, icon: ImageVector? = null) {
    Surface(shape = CircleShape, color = container, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(14.dp))
            Text(text, color = content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun ChoiceRow(content: @Composable RowScope.() -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)

/** Short burst of falling confetti. Animates only while visible, then stops drawing frames. */
@Composable
fun Confetti(modifier: Modifier = Modifier, key: Any = Unit, colors: List<Color>) {
    val particles = remember(key) {
        List(90) {
            Particle(
                x = Random.nextFloat(), delay = Random.nextFloat() * 0.6f, speed = 0.35f + Random.nextFloat() * 0.45f,
                drift = (Random.nextFloat() - 0.5f) * 0.3f, spin = Random.nextFloat() * 720f - 360f,
                color = colors[it % colors.size], wide = Random.nextBoolean(),
            )
        }
    }
    val elapsed = remember(key) { mutableLongStateOf(0L) }
    var finished by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        val start = withFrameNanos { it }
        while (elapsed.longValue < CONFETTI_NANOS) {
            elapsed.longValue = withFrameNanos { it } - start
        }
        finished = true
    }
    if (finished) return
    // The clock is read only inside the draw block, so each frame redraws without recomposing.
    Canvas(modifier) {
        val t = elapsed.longValue / 1_000_000_000f
        particles.forEach { p ->
            val local = t - p.delay
            if (local < 0f) return@forEach
            val y = -0.05f + local * p.speed
            if (y > 1.1f) return@forEach
            val x = p.x + p.drift * local + sin(local * 4f + p.x * 10f) * 0.02f
            val angle = p.spin * local
            val w = if (p.wide) 14f else 8f
            rotate(angle, Offset(x * size.width, y * size.height)) {
                drawRect(p.color, Offset(x * size.width - w / 2, y * size.height - 5f), Size(w, 10f))
            }
        }
    }
}

private const val CONFETTI_NANOS = 3_600_000_000L

private data class Particle(val x: Float, val delay: Float, val speed: Float, val drift: Float, val spin: Float, val color: Color, val wide: Boolean)

/** Radiating rings used for celebratory and "listening" states. */
@Composable
fun PulseRings(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "phase")
    Canvas(modifier) {
        repeat(3) { i ->
            val p = (phase + i / 3f) % 1f
            drawCircle(color.copy(alpha = (1f - p) * 0.35f), radius = size.minDimension / 2 * (0.35f + p * 0.65f), style = Stroke(3.dp.toPx()))
        }
    }
}

class Haptics(private val view: android.view.View, private val enabled: Boolean) {
    fun tick() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        if (!enabled) return
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    }

    fun heavy() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val view = LocalView.current
    return remember(view, enabled) { Haptics(view, enabled) }
}

fun ExerciseType.icon(): ImageVector = when (this) {
    ExerciseType.SQUAT -> Icons.Default.AccessibilityNew
    ExerciseType.PUSH_UP -> Icons.Default.FitnessCenter
    ExerciseType.JUMPING_JACK -> Icons.Default.SportsGymnastics
    ExerciseType.LUNGE -> Icons.AutoMirrored.Filled.DirectionsRun
    ExerciseType.PLANK -> Icons.Default.Timer
    ExerciseType.CRUNCH -> Icons.Default.AirlineSeatFlat
    ExerciseType.SHOULDER_PRESS -> Icons.Default.SportsMartialArts
    ExerciseType.MOUNTAIN_CLIMBER -> Icons.Default.Bolt
    ExerciseType.GLUTE_BRIDGE -> Icons.Default.SelfImprovement
    ExerciseType.HIGH_KNEES -> Icons.AutoMirrored.Filled.DirectionsRun
}

fun Badge.icon(): ImageVector = when (this) {
    Badge.FIRST_WORKOUT -> Icons.Default.Flag
    Badge.FIVE_WORKOUTS, Badge.TWENTY_FIVE_WORKOUTS -> Icons.Default.FitnessCenter
    Badge.HUNDRED_WORKOUTS -> Icons.Default.WorkspacePremium
    Badge.REPS_100, Badge.REPS_1000 -> Icons.Default.Repeat
    Badge.STREAK_3, Badge.STREAK_7, Badge.STREAK_30 -> Icons.Default.LocalFireDepartment
    Badge.PLANK_60 -> Icons.Default.Timer
    Badge.TARGETS_10 -> Icons.Default.GpsFixed
    Badge.EXPLORER -> Icons.Default.Explore
    Badge.ALL_ROUNDER -> Icons.Default.AllInclusive
    Badge.EARLY_BIRD -> Icons.Default.WbSunny
    Badge.NIGHT_OWL -> Icons.Default.Bedtime
    Badge.WEEKLY_GOAL -> Icons.Default.CalendarMonth
    Badge.CIRCUIT -> Icons.Default.MilitaryTech
    Badge.CHALLENGER -> Icons.Default.EmojiEvents
}
