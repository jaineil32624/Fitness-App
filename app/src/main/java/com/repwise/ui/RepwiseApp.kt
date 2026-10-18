package com.repwise.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repwise.audio.MusicTrack
import com.repwise.domain.AccentTheme
import com.repwise.domain.ExerciseType
import com.repwise.ui.components.IconBubble
import com.repwise.ui.components.Pill
import com.repwise.ui.components.icon
import com.repwise.ui.home.HomeScreen
import com.repwise.ui.onboarding.OnboardingScreen
import com.repwise.ui.profile.ProfileScreen
import com.repwise.ui.progress.ProgressScreen
import com.repwise.ui.sounds.SoundsScreen
import com.repwise.ui.theme.LocalRepwiseColors
import com.repwise.ui.theme.RepwiseTheme
import com.repwise.ui.workout.IntervalScreen
import com.repwise.ui.workout.LiveWorkoutScreen
import com.repwise.ui.workout.SummaryScreen
import com.repwise.ui.workout.TrainScreen

/** True on tablets and unfolded foldables, where content is laid out with a navigation rail. */
val LocalWideLayout = compositionLocalOf { false }

@Composable
fun RepwiseApp(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    // A theme can stay selected after history is cleared; fall back if it's no longer unlocked.
    val accent = settings.accent.takeIf { game.level.level >= it.unlockLevel } ?: AccentTheme.FOREST
    RepwiseTheme(settings.themeMode, accent) {
        SystemBars()
        if (!settings.onboardingDone) {
            OnboardingScreen(onFinish = vm::completeOnboarding)
        } else {
            AppContent(vm)
        }
    }
}

@Composable
private fun SystemBars() {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val dark = LocalRepwiseColors.current.isDark
    val scrim = MaterialTheme.colorScheme.background.copy(alpha = 0.01f).toArgb()
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(vm: AppViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val destination by vm.destination.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    var briefing by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    var pending by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    var showSettingsPrompt by remember { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val exercise = pending
        pending = null
        when {
            granted && exercise != null -> vm.startLive(exercise)
            !granted && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) -> showSettingsPrompt = true
            !granted -> vm.message("Camera access is needed for live pose tracking.")
        }
    }

    fun start(exercise: ExerciseType, withMusic: Boolean) {
        briefing = null
        if (withMusic) vm.audio.playTrack(MusicTrack.recommendedFor(exercise))
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            vm.startLive(exercise)
        } else {
            pending = exercise
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    AnimatedContent(
        targetState = destination,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith fadeOut(tween(200))
        },
        label = "destination",
    ) { target ->
        when (target) {
            Destination.Main -> MainScaffold(vm, onExercise = { briefing = it })
            Destination.Live -> LiveWorkoutScreen(vm)
            Destination.Interval -> IntervalScreen(vm)
            is Destination.Summary -> SummaryScreen(target.reward, vm)
        }
    }

    briefing?.let { exercise ->
        ExerciseSheet(
            exercise = exercise,
            target = exercise.target(profile.difficulty),
            onDismiss = { briefing = null },
            onStart = { withMusic -> start(exercise, withMusic) },
        )
    }

    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = { showSettingsPrompt = false },
            icon = { Icon(Icons.Default.CameraAlt, null) },
            title = { Text("Camera access is off") },
            text = { Text("Repwise needs the camera to see your pose and count reps. Video never leaves your device. You can turn access on in Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsPrompt = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Open Settings") }
            },
            dismissButton = { TextButton(onClick = { showSettingsPrompt = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel, onExercise: (ExerciseType) -> Unit) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        androidx.compose.runtime.CompositionLocalProvider(LocalWideLayout provides wide) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = { if (!wide) BottomBar(tab, vm::selectTab) },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (wide) SideRail(tab, vm::selectTab)
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val forward = targetState.ordinal > initialState.ordinal
                                (fadeIn(tween(260)) + slideInHorizontally(tween(300)) { w -> if (forward) w / 10 else -w / 10 }) togetherWith
                                    (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { w -> if (forward) -w / 14 else w / 14 })
                            },
                            label = "tab",
                            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                        ) { current ->
                            when (current) {
                                Tab.HOME -> HomeScreen(vm, onExercise)
                                Tab.TRAIN -> TrainScreen(vm, onExercise)
                                Tab.PROGRESS -> ProgressScreen(vm)
                                Tab.SOUNDS -> SoundsScreen(vm)
                                Tab.PROFILE -> ProfileScreen(vm)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Tab.icon(selected: Boolean) = when (this) {
    Tab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    Tab.TRAIN -> if (selected) Icons.Filled.FitnessCenter else Icons.Outlined.FitnessCenter
    Tab.PROGRESS -> if (selected) Icons.Filled.Insights else Icons.Outlined.Insights
    Tab.SOUNDS -> if (selected) Icons.Filled.GraphicEq else Icons.Outlined.GraphicEq
    Tab.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
}

@Composable
private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
        Tab.entries.forEach { destination ->
            NavigationBarItem(
                selected = tab == destination,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon(tab == destination), contentDescription = null) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun SideRail(tab: Tab, onSelect: (Tab) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.statusBars).navigationBarsPadding(),
    ) {
        Spacer(Modifier.height(12.dp))
        Tab.entries.forEach { destination ->
            NavigationRailItem(
                selected = tab == destination,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon(tab == destination), contentDescription = null) },
                label = { Text(destination.label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseSheet(exercise: ExerciseType, target: Int, onDismiss: () -> Unit, onStart: (Boolean) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var withMusic by rememberSaveable { mutableStateOf(false) }
    val track = MusicTrack.recommendedFor(exercise)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(exercise.icon(), size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(exercise.title, style = MaterialTheme.typography.headlineSmall)
                    Text(exercise.group.label + if (exercise.isTimed) " · timed hold" else " · rep counting", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (exercise.isTimed) "Goal: hold ${target}s" else "Goal: $target reps")
                Pill("~${exercise.met} MET", container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text("Setup: ${exercise.cue} Place your phone a few feet away in a bright space.", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Play ${track.title}", style = MaterialTheme.typography.titleSmall)
                    Text("Recommended for this move", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = withMusic, onCheckedChange = { withMusic = it })
            }
            Button(onClick = { onStart(withMusic) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Start tracking", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
