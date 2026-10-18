package com.repwise.ui.workout

import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repwise.audio.MusicTrack
import com.repwise.camera.CameraSession
import com.repwise.camera.LandmarkPoint
import com.repwise.domain.Gamification
import com.repwise.domain.estimateCalories
import com.repwise.ui.AppViewModel
import com.repwise.ui.LiveSession
import com.repwise.ui.SessionEvent
import com.repwise.ui.components.Confetti
import com.repwise.ui.components.GlassPanel
import com.repwise.ui.components.ProgressRing
import com.repwise.ui.components.formatDuration
import com.repwise.ui.components.rememberHaptics
import com.repwise.ui.theme.LocalRepwiseColors

private val LiveGreen = Color(0xFF8EE0A9)
private val LiveMuted = Color(0xFFB3C0B8)

@Composable
fun LiveWorkoutScreen(vm: AppViewModel) {
    val session by vm.live.collectAsStateWithLifecycle()
    val current = session ?: return
    val settings by vm.settings.collectAsStateWithLifecycle()
    val player by vm.audio.state.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val haptics = rememberHaptics(settings.haptics)
    var confirmEnd by remember { mutableStateOf(false) }
    var celebrate by remember { mutableIntStateOf(0) }
    val repPulse = remember { Animatable(1f) }

    BackHandler { confirmEnd = true }
    KeepScreenOn()
    ForegroundTracker(vm)

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                SessionEvent.Rep -> {
                    haptics.tick()
                    repPulse.snapTo(1.18f)
                    repPulse.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                }
                SessionEvent.TargetHit -> {
                    haptics.confirm()
                    celebrate++
                    repPulse.snapTo(1.3f)
                    repPulse.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
                }
                SessionEvent.PhaseChange -> Unit
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        CameraLayer(vm, current)

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            TopControls(
                title = current.exercise.title,
                musicOn = player.playing,
                paused = current.paused,
                onClose = { confirmEnd = true },
                onMusic = {
                    if (player.playing) vm.audio.pause()
                    else if (player.hasContent) vm.audio.play()
                    else vm.audio.playTrack(MusicTrack.recommendedFor(current.exercise))
                },
                onFlip = vm::flipCamera,
                onPause = vm::toggleLivePause,
            )
            Spacer(Modifier.height(10.dp))
            FeedbackPill(current)
            Spacer(Modifier.weight(1f))
            if (!landscape) {
                StatsPanel(current, profile.weightKg, repPulse.value, onFinish = { confirmEnd = true })
            }
        }
        if (landscape) {
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 360.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                StatsPanel(current, profile.weightKg, repPulse.value, onFinish = { confirmEnd = true })
            }
        }

        TargetBanner(visible = current.targetHit, modifier = Modifier.align(Alignment.Center))
        if (celebrate > 0) {
            Confetti(
                Modifier.fillMaxSize(), key = celebrate,
                colors = listOf(LiveGreen, Color.White, LocalRepwiseColors.current.glow, Color(0xFFFFD66B)),
            )
        }
    }

    if (confirmEnd) {
        EndDialog(
            session = current,
            onKeepGoing = { confirmEnd = false },
            onSave = { confirmEnd = false; vm.finishLive(save = true) },
            onDiscard = { confirmEnd = false; vm.finishLive(save = false) },
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun ForegroundTracker(vm: AppViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.setForeground(true)
                Lifecycle.Event.ON_PAUSE -> vm.setForeground(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            vm.setForeground(true)
        }
    }
}

@Composable
private fun CameraLayer(vm: AppViewModel, session: LiveSession) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also {
                    it.scaleType = PreviewView.ScaleType.FILL_CENTER
                    it.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = it
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        PoseOverlay(session, Modifier.fillMaxSize())
        when {
            session.paused -> CenterNotice(Icons.Default.Pause, "Paused", "Camera is off to save battery. Tap play to resume.")
            session.cameraError != null -> CenterNotice(Icons.Default.VideocamOff, "Camera unavailable", session.cameraError)
            !session.feedback.visible -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("Finding your pose…", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(session.exercise.cue, color = LiveMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp))
            }
        }
    }

    val analyzer = vm.liveAnalyzer()
    DisposableEffect(previewView, session.frontCamera, session.paused) {
        val view = previewView
        val camera = if (view != null && analyzer != null && !session.paused) {
            CameraSession(context, lifecycleOwner, view, analyzer, session.frontCamera, vm::onPoseFeedback, vm::onCameraError)
        } else null
        camera?.start()
        onDispose { camera?.close() }
    }
}

@Composable
private fun CenterNotice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(body, color = LiveMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Draws the tracked skeleton using the same FILL_CENTER crop and mirroring as the preview,
 * so joints line up with the body on screen regardless of aspect ratio.
 */
@Composable
private fun PoseOverlay(session: LiveSession, modifier: Modifier) {
    val points = session.feedback.points
    if (points.isEmpty() || session.paused) return
    val mirror = session.frontCamera
    val bone = if (session.feedback.goodForm) LiveGreen else Color.White.copy(alpha = 0.85f)
    val joint = LocalRepwiseColors.current.heroEnd
    Canvas(modifier) {
        val imgW = session.feedback.imageWidth.toFloat()
        val imgH = session.feedback.imageHeight.toFloat()
        val (scaleX, scaleY, dx, dy) = if (imgW > 0 && imgH > 0) {
            val scale = maxOf(size.width / imgW, size.height / imgH)
            listOf(imgW * scale, imgH * scale, (size.width - imgW * scale) / 2, (size.height - imgH * scale) / 2)
        } else listOf(size.width, size.height, 0f, 0f)
        fun map(p: LandmarkPoint) = Offset(dx + (if (mirror) 1f - p.x else p.x) * scaleX, dy + p.y * scaleY)
        BONES.forEach { (start, end) ->
            val a = points[start]
            val b = points[end]
            if (a != null && b != null) drawLine(bone, map(a), map(b), 6f, cap = StrokeCap.Round)
        }
        points.forEach { (type, p) ->
            if (type in JOINTS) {
                drawCircle(Color.White, 8f, map(p))
                drawCircle(joint, 4.5f, map(p))
            }
        }
    }
}

private val BONES = listOf(
    11 to 13, 13 to 15, 12 to 14, 14 to 16, 11 to 12, 11 to 23, 12 to 24,
    23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28, 27 to 31, 28 to 32,
)
private val JOINTS = BONES.flatMap { listOf(it.first, it.second) }.toSet()

@Composable
private fun TopControls(
    title: String,
    musicOn: Boolean,
    paused: Boolean,
    onClose: () -> Unit,
    onMusic: () -> Unit,
    onFlip: () -> Unit,
    onPause: () -> Unit,
) {
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "End workout", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                LiveIndicator(paused)
            }
            IconButton(onClick = onMusic) {
                Icon(if (musicOn) Icons.Default.MusicNote else Icons.Default.MusicOff, if (musicOn) "Pause music" else "Play music", tint = if (musicOn) LiveGreen else Color.White)
            }
            IconButton(onClick = onFlip) { Icon(Icons.Default.Cameraswitch, "Switch camera", tint = Color.White) }
            IconButton(onClick = onPause) {
                Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (paused) "Resume" else "Pause", tint = Color.White)
            }
        }
    }
}

@Composable
private fun LiveIndicator(paused: Boolean) {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(1f, 0.25f, infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse), label = "dot")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).graphicsLayer { this.alpha = if (paused) 1f else alpha }.clip(CircleShape).background(if (paused) LiveMuted else Color(0xFFFF5A5A)))
        Spacer(Modifier.width(5.dp))
        Text(if (paused) "PAUSED" else "LIVE", color = if (paused) LiveMuted else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun FeedbackPill(session: LiveSession) {
    val good = session.feedback.goodForm
    val color by animateColorAsState(if (good) LocalRepwiseColors.current.heroEnd else Color.Black, tween(300), label = "feedback")
    val message = session.cameraError ?: session.feedback.message
    GlassPanel(shape = RoundedCornerShape(16.dp), tint = color) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (good) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(message, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatsPanel(session: LiveSession, weightKg: Float, pulse: Float, onFinish: () -> Unit) {
    val active = session.feedback.active && !session.paused
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = session.reps / session.target.toFloat(),
                    size = 112.dp, stroke = 10.dp,
                    color = LiveGreen, track = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${session.reps}", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (session.exercise.isTimed) "of ${session.target}s" else "of ${session.target}",
                            color = LiveMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    LiveStat("ACTIVE", formatDuration(session.activeSeconds), highlight = active)
                    LiveStat("ELAPSED", formatDuration(session.elapsedSeconds))
                    LiveStat("EST. KCAL", "${estimateCalories(session.exercise.met, weightKg, session.activeSeconds)}")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (active) Icons.Default.CheckCircle else Icons.Default.Timer, null, tint = if (active) LiveGreen else LiveMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        session.paused -> "PAUSED · TIMERS STOPPED"
                        active -> "MOVEMENT DETECTED · ACTIVE TIME COUNTING"
                        else -> "WAITING FOR MOVEMENT"
                    },
                    color = if (active) LiveGreen else LiveMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) { Text("Finish workout", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun LiveStat(label: String, value: String, highlight: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, color = LiveMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.width(72.dp))
        Text(value, color = if (highlight) LiveGreen else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TargetBanner(visible: Boolean, modifier: Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            kotlinx.coroutines.delay(2_200)
            shown = false
        }
    }
    AnimatedVisibility(
        visible = shown, modifier = modifier,
        enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(),
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        GlassPanel(shape = RoundedCornerShape(24.dp), tint = LocalRepwiseColors.current.heroEnd) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Target hit!", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text("+${Gamification.TARGET_BONUS_XP} XP bonus · keep going", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EndDialog(session: LiveSession, onKeepGoing: () -> Unit, onSave: () -> Unit, onDiscard: () -> Unit) {
    if (session.hasProgress) {
        AlertDialog(
            onDismissRequest = onKeepGoing,
            title = { Text("Finish workout?") },
            text = {
                val movement = if (session.exercise.isTimed) "${session.reps}s held" else "${session.reps} reps"
                Text("$movement · ${formatDuration(session.activeSeconds)} active. Save it to earn XP and keep your streak.")
            },
            confirmButton = { Button(onClick = onSave) { Text("Save") } },
            dismissButton = {
                Row {
                    TextButton(onClick = onDiscard) { Text("Discard") }
                    TextButton(onClick = onKeepGoing) { Text("Keep going") }
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onKeepGoing,
            title = { Text("End session?") },
            text = { Text("No movement has been tracked yet, so nothing will be saved.") },
            confirmButton = { TextButton(onClick = onDiscard) { Text("End") } },
            dismissButton = { TextButton(onClick = onKeepGoing) { Text("Keep going") } },
        )
    }
}
