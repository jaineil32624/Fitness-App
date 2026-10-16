package com.repwise

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.repwise.camera.ExerciseType
import com.repwise.camera.LandmarkPoint
import com.repwise.camera.PoseFeedback
import com.repwise.camera.PoseWorkoutAnalyzer
import com.repwise.data.UserProfile
import com.repwise.data.WorkoutRecord
import com.repwise.data.WorkoutStore
import com.repwise.updates.AppRelease
import com.repwise.updates.GitHubReleaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val Forest = Color(0xFF327A5B)
private val Ink = Color(0xFF1C2822)
private val Muted = Color(0xFF737C76)
private val CanvasBg = Color(0xFFF5F6F2)
private val Mint = Color(0xFFE5F0E9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(245, 246, 242)
        window.navigationBarColor = android.graphics.Color.rgb(245, 246, 242)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { RepwiseApp() }
    }
}

private enum class Tab(val label: String) {
    HOME("Home"), WORKOUT("Workout"), PROGRESS("Progress"), PROFILE("Profile")
}

@Composable
private fun RepwiseApp() {
    val context = LocalContext.current
    val store = remember { WorkoutStore(context) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var records by remember { mutableStateOf(store.records()) }
    var profile by remember { mutableStateOf(store.profile()) }
    var selectedExercise by remember { mutableStateOf<ExerciseType?>(null) }
    var workoutStarted by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf(PoseFeedback(emptyMap(), "Position your full body in frame", 0, false, false)) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) workoutStarted = true else statusMessage = "Camera access is needed for live pose tracking."
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(workoutStarted) {
        if (workoutStarted) while (true) {
            delay(1_000)
            seconds++
        }
    }

    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Forest,
            onPrimary = Color.White,
            secondary = Color(0xFFB8D9C5),
            background = CanvasBg,
            surface = Color.White,
            onSurface = Ink,
            onBackground = Ink,
        ),
    ) {
        if (workoutStarted && selectedExercise != null) {
            LiveWorkoutScreen(
                exercise = selectedExercise!!,
                weightKg = profile.weightKg,
                seconds = seconds,
                feedback = feedback,
                onFeedback = { feedback = it },
                onEnd = {
                    val exercise = selectedExercise!!
                    val calories = (exercise.met * profile.weightKg * (seconds / 3600f)).toInt().coerceAtLeast(1)
                    store.save(WorkoutRecord(exercise = exercise.title, reps = feedback.reps, seconds = seconds, calories = calories))
                    records = store.records()
                    workoutStarted = false
                    selectedExercise = null
                    seconds = 0
                    feedback = PoseFeedback(emptyMap(), "Position your full body in frame", 0, false, false)
                    statusMessage = "Workout saved. Nice work!"
                    tab = Tab.HOME
                },
            )
        } else {
            Scaffold(
                containerColor = CanvasBg,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(containerColor = Color.White, windowInsets = WindowInsets.navigationBars) {
                        Tab.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = tab == destination,
                                onClick = { tab = destination },
                                icon = {
                                    Icon(
                                        when (destination) {
                                            Tab.HOME -> Icons.Default.Home
                                            Tab.WORKOUT -> Icons.Default.FitnessCenter
                                            Tab.PROGRESS -> Icons.Default.QueryStats
                                            Tab.PROFILE -> Icons.Default.Person
                                        },
                                        contentDescription = destination.label,
                                    )
                                },
                                label = { Text(destination.label, fontSize = 11.sp) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            name = profile.name,
                            records = records,
                            onStart = { tab = Tab.WORKOUT },
                            onWorkout = { tab = Tab.WORKOUT },
                            onProgress = { tab = Tab.PROGRESS },
                        )
                        Tab.WORKOUT -> WorkoutScreen(
                            onStart = { exercise ->
                                selectedExercise = exercise
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    workoutStarted = true
                                } else {
                                    cameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            },
                        )
                        Tab.PROGRESS -> ProgressScreen(records = records, weeklyGoal = profile.weeklyGoal)
                        Tab.PROFILE -> ProfileScreen(
                            profile = profile,
                            records = records,
                            message = statusMessage,
                            onSave = { updated ->
                                profile = updated
                                store.saveProfile(updated)
                                statusMessage = "Profile saved on this device."
                            },
                            onMessage = { statusMessage = it },
                        )
                    }
                    statusMessage?.let { message ->
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Ink,
                            shadowElevation = 8.dp,
                        ) {
                            Text(message, color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
                        }
                        LaunchedEffect(message) { delay(3_500); if (statusMessage == message) statusMessage = null }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenColumn(
    scrollable: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(horizontal = 20.dp)
            .then(scrollModifier),
        content = content,
    )
}

@Composable
private fun HomeScreen(
    name: String,
    records: List<WorkoutRecord>,
    onStart: () -> Unit,
    onWorkout: () -> Unit,
    onProgress: () -> Unit,
) {
    val week = records.filter { it.date >= java.time.LocalDate.now().minusDays(6).toString() }
    val workoutCount = week.size
    val totalMinutes = week.sumOf { it.seconds } / 60
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 26.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("REPWISE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Forest, letterSpacing = 2.sp)
                    Text("Good day, $name", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                Surface(shape = CircleShape, color = Mint, modifier = Modifier.size(46.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.FitnessCenter, null, tint = Forest) }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
                Column(Modifier.padding(22.dp)) {
                    Text("YOUR NEXT REP STARTS HERE", fontSize = 11.sp, color = Color(0xFFA9C8B5), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Move better.\nFeel stronger.", fontSize = 30.sp, lineHeight = 35.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Your personal camera-powered workout companion.", color = Color(0xFFD0D8D2), fontSize = 14.sp)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = Forest), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start a workout")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, null)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("THIS WEEK", workoutCount.toString(), "workouts", Modifier.weight(1f))
                MetricCard("ACTIVE TIME", totalMinutes.toString(), "minutes", Modifier.weight(1f))
                MetricCard("TOTAL", records.sumOf { it.calories }.toString(), "kcal", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recommended for you", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("See all", color = Forest, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onWorkout))
            }
        }
        items(ExerciseType.entries.take(3)) { exercise ->
            ExerciseRow(exercise = exercise, onClick = onWorkout)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), modifier = Modifier.clickable(onClick = onProgress)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Mint, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.EmojiEvents, null, tint = Forest) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your progress, your pace", fontWeight = FontWeight.Bold)
                        Text("Build a streak one session at a time.", fontSize = 13.sp, color = Muted)
                    }
                    Icon(Icons.Default.ArrowForward, null, tint = Forest)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
            Spacer(Modifier.height(7.dp))
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(unit, fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseType, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = Mint, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.FitnessCenter, null, tint = Forest) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.title, fontWeight = FontWeight.Bold)
                Text(if (exercise.isTimed) "Timed hold • core" else "Rep counting • full body", fontSize = 12.sp, color = Muted)
            }
            Icon(Icons.Default.ArrowForward, null, tint = Forest)
        }
    }
}

@Composable
private fun WorkoutScreen(onStart: (ExerciseType) -> Unit) {
    ScreenColumn(scrollable = true) {
        Text("Choose your move", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("We'll track your movement right on your device.", color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
        ExerciseType.entries.forEach { exercise ->
            ExerciseRow(exercise = exercise) { onStart(exercise) }
            Spacer(Modifier.height(12.dp))
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Mint)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.CameraAlt, null, tint = Forest)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Set your phone a few feet away in a bright space. Keep the exercise area visible so the pose tracker can see your joints.",
                    color = Ink,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun LiveWorkoutScreen(
    exercise: ExerciseType,
    weightKg: Float,
    seconds: Int,
    feedback: PoseFeedback,
    onFeedback: (PoseFeedback) -> Unit,
    onEnd: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(
            Modifier.fillMaxWidth().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp, start = 18.dp, end = 18.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "End workout", tint = Color.White, modifier = Modifier.clickable(onClick = onEnd))
            Text(exercise.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("LIVE", color = Color(0xFF8ED5A9), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF26342D))) {
            AndroidView(
                factory = { ctx -> PreviewView(ctx).also { it.scaleType = PreviewView.ScaleType.FILL_CENTER; previewView = it } },
                modifier = Modifier.fillMaxSize(),
            )
            PoseOverlay(feedback.points, Modifier.fillMaxSize())
            if (!feedback.visible) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text("Finding your pose…", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Surface(
                Modifier.align(Alignment.TopStart).padding(12.dp),
                color = if (feedback.goodForm) Forest else Color(0xCC1C2822),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(feedback.message, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            WorkoutStat(if (exercise.isTimed) "SECONDS HELD" else "REPS", if (exercise.isTimed) feedback.reps.toString() else feedback.reps.toString())
            WorkoutStat("DURATION", formatDuration(seconds))
            WorkoutStat("CALORIES", ((exercise.met * weightKg * seconds / 3600f).toInt()).toString())
        }
        Button(
            onClick = onEnd,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 18.dp).height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
        ) { Text("Finish workout", fontWeight = FontWeight.Bold) }
    }
    DisposableEffect(previewView) {
        val view = previewView
        val camera = view?.let { CameraSession(context, lifecycleOwner, it, exercise, onFeedback) }
        camera?.start()
        onDispose { camera?.close() }
    }
}

@Composable
private fun WorkoutStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFFB3C0B8), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PoseOverlay(points: Map<Int, LandmarkPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bones = listOf(
            11 to 13, 13 to 15, 12 to 14, 14 to 16, 11 to 12, 11 to 23, 12 to 24,
            23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28, 27 to 31, 28 to 32,
        )
        bones.forEach { (start, end) ->
            val a = points[start]
            val b = points[end]
            if (a != null && b != null) {
                drawLine(
                    Color(0xFF8EE0A9),
                    Offset(size.width * (1f - a.x), size.height * a.y),
                    Offset(size.width * (1f - b.x), size.height * b.y),
                    5f,
                    cap = StrokeCap.Round,
                )
            }
        }
        points.values.forEach { p ->
            drawCircle(Color.White, 7f, Offset(size.width * (1f - p.x), size.height * p.y))
            drawCircle(Forest, 4f, Offset(size.width * (1f - p.x), size.height * p.y))
        }
    }
}

private class CameraSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    exercise: ExerciseType,
    private val onFeedback: (PoseFeedback) -> Unit,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
    )
    private val analyzer = PoseWorkoutAnalyzer(exercise)
    private val closed = AtomicBoolean(false)
    private val processing = AtomicBoolean(false)
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (closed.get()) return@addListener
            val cameraProvider = future.get()
            provider = cameraProvider
            val cameraPreview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { useCase ->
                    useCase.setAnalyzer(executor) { proxy ->
                        val image = proxy.image
                        if (image == null || !processing.compareAndSet(false, true)) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        val input = InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)
                        detector.process(input)
                            .addOnSuccessListener(executor) { pose ->
                                if (!closed.get()) {
                                    val rotated = proxy.imageInfo.rotationDegrees % 180 != 0
                                    val result = analyzer.analyze(
                                        pose,
                                        imageWidth = if (rotated) proxy.height else proxy.width,
                                        imageHeight = if (rotated) proxy.width else proxy.height,
                                    )
                                    ContextCompat.getMainExecutor(context).execute { if (!closed.get()) onFeedback(result) }
                                }
                            }
                            .addOnCompleteListener(executor) {
                                processing.set(false)
                                proxy.close()
                            }
                    }
                }
            preview = cameraPreview
            imageAnalysis = analysis
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, cameraPreview, analysis)
            } catch (error: Exception) {
                onFeedback(PoseFeedback(emptyMap(), "Camera unavailable: ${error.localizedMessage ?: "check camera access"}", 0, false, false))
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun close() {
        closed.set(true)
        provider?.let { cameraProvider ->
            listOfNotNull(preview, imageAnalysis).forEach { cameraProvider.unbind(it) }
        }
        detector.close()
        executor.shutdown()
    }
}

@Composable
private fun ProgressScreen(records: List<WorkoutRecord>, weeklyGoal: Int) {
    val week = records.filter { it.date >= java.time.LocalDate.now().minusDays(6).toString() }
    val weekMinutes = week.sumOf { it.seconds } / 60
    val streak = calculateStreak(records)
    ScreenColumn(scrollable = true) {
        Text("Your progress", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Small steps add up. Keep showing up.", color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("WORKOUTS", week.size.toString(), "this week", Modifier.weight(1f))
            MetricCard("ACTIVE TIME", weekMinutes.toString(), "minutes", Modifier.weight(1f))
            MetricCard("STREAK", streak.toString(), "days", Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("Weekly goal", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("${week.size} of $weeklyGoal workouts", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (week.size.toFloat() / weeklyGoal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Forest,
                    trackColor = Mint,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Achievements", fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Spacer(Modifier.height(10.dp))
        AchievementCard("First session", "You got moving — the hardest rep is starting.", records.isNotEmpty())
        Spacer(Modifier.height(9.dp))
        AchievementCard("Five workouts", "Build consistency, one session at a time.", records.size >= 5)
        Spacer(Modifier.height(9.dp))
        AchievementCard("Three-day streak", "Keep your momentum going.", streak >= 3)
        Spacer(Modifier.height(20.dp))
        Text("Recent workouts", fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Spacer(Modifier.height(8.dp))
        if (records.isEmpty()) Text("Your completed sessions will show up here.", color = Muted, modifier = Modifier.padding(vertical = 18.dp))
        records.take(10).forEach { record ->
            HistoryRow(record)
            Divider(color = Color(0xFFE8ECE8))
        }
    }
}

@Composable
private fun AchievementCard(title: String, subtitle: String, unlocked: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = if (unlocked) Mint else Color(0xFFF0F1EF), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.EmojiEvents, null, tint = if (unlocked) Forest else Muted) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            if (unlocked) Icon(Icons.Default.CheckCircle, null, tint = Forest)
        }
    }
}

@Composable
private fun HistoryRow(record: WorkoutRecord) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = Mint, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.FitnessCenter, null, tint = Forest) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(record.exercise, fontWeight = FontWeight.SemiBold)
            val movement = if (record.exercise == ExerciseType.PLANK.title) "${record.reps} sec held" else "${record.reps} reps"
            Text("$movement  •  ${formatDuration(record.seconds)}  •  ${record.calories} kcal", color = Muted, fontSize = 12.sp)
        }
        Text(record.date, color = Muted, fontSize = 11.sp)
    }
}

private fun calculateStreak(records: List<WorkoutRecord>): Int {
    val dates = records.map { it.date }.toSet()
    var date = java.time.LocalDate.now()
    if (date.toString() !in dates) date = date.minusDays(1)
    var count = 0
    while (date.toString() in dates) {
        count++
        date = date.minusDays(1)
    }
    return count
}

@Composable
private fun ProfileScreen(
    profile: UserProfile,
    records: List<WorkoutRecord>,
    message: String?,
    onSave: (UserProfile) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var weight by remember(profile.weightKg) { mutableStateOf(profile.weightKg.toInt().toString()) }
    var goal by remember(profile.weeklyGoal) { mutableStateOf(profile.weeklyGoal.toString()) }
    var difficulty by remember(profile.difficulty) { mutableStateOf(profile.difficulty) }
    var owner by remember(profile.githubOwner) { mutableStateOf(profile.githubOwner) }
    var repo by remember(profile.githubRepo) { mutableStateOf(profile.githubRepo) }
    var loading by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadComplete by remember { mutableStateOf(false) }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        while (!downloadComplete) {
            val query = manager.query(android.app.DownloadManager.Query().setFilterById(id))
            query.use { cursor ->
                if (cursor.moveToFirst()) {
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))) {
                        android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                            downloadComplete = true
                            onMessage("APK downloaded. Tap Install downloaded APK to continue.")
                        }
                        android.app.DownloadManager.STATUS_FAILED -> {
                            downloadComplete = true
                            onMessage("APK download failed. Please try again.")
                        }
                    }
                }
            }
            if (!downloadComplete) delay(1_000)
        }
    }

    ScreenColumn {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                Text("Your profile", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Personalize your training and updates.", color = Muted, modifier = Modifier.padding(top = 5.dp))
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text("Training profile", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            weight, { weight = it.filter(Char::isDigit).take(3) }, label = { Text("Weight (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            goal, { goal = it.filter(Char::isDigit).take(2) }, label = { Text("Weekly workout goal") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Difficulty", color = Muted, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Beginner", "Intermediate", "Advanced").forEach { level ->
                                val selected = difficulty == level
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) Forest else CanvasBg,
                                    modifier = Modifier.clickable { difficulty = level },
                                ) {
                                    Text(level, color = if (selected) Color.White else Ink, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
                                }
                            }
                        }
                        Button(
                            onClick = {
                                val safeWeight = weight.toFloatOrNull()?.coerceIn(25f, 250f) ?: profile.weightKg
                                val safeGoal = goal.toIntOrNull()?.coerceIn(1, 14) ?: profile.weeklyGoal
                                onSave(profile.copy(name = name.trim().ifBlank { "Athlete" }, weightKg = safeWeight, weeklyGoal = safeGoal, difficulty = difficulty))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Save profile") }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("App updates", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Check for an APK in the latest GitHub release.", color = Muted, fontSize = 13.sp)
                        OutlinedTextField(owner, { owner = it }, label = { Text("GitHub owner") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(repo, { repo = it }, label = { Text("Repository") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !loading,
                                onClick = {
                                    loading = true
                                    scope.launch {
                                        try {
                                            val latest = withContext(Dispatchers.IO) { GitHubReleaseClient(context).latest(owner.trim(), repo.trim()) }
                                            release = latest
                                            onSave(profile.copy(githubOwner = owner.trim(), githubRepo = repo.trim()))
                                            onMessage("Found ${latest.tag}. Review the release before installing.")
                                        } catch (error: Exception) {
                                            onMessage(error.message ?: "Could not check for a release.")
                                        } finally {
                                            loading = false
                                        }
                                    }
                                },
                            ) {
                                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                else Text("Check releases")
                            }
                            if (release != null) {
                                OutlinedButton(onClick = {
                                    try {
                                        downloadId = GitHubReleaseClient(context).download(release!!)
                                        downloadComplete = false
                                        onMessage("Downloading ${release!!.tag}…")
                                    } catch (error: Exception) {
                                        onMessage(error.message ?: "Could not start APK download.")
                                    }
                                }) { Text("Download APK") }
                            }
                        }
                        if (release != null) {
                            Text("Latest: ${release!!.tag}", color = Forest, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        if (downloadComplete && release != null) {
                            Button(onClick = {
                                val apkUri = downloadId?.let { id ->
                                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).getUriForDownloadedFile(id)
                                }
                                if (apkUri == null) {
                                    onMessage("Downloaded APK could not be opened. Please download it again.")
                                } else if (android.os.Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                                    onMessage("Allow installs for Repwise, return here, then tap Install downloaded APK.")
                                } else {
                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(installIntent)
                                    } catch (error: Exception) {
                                        onMessage(error.message ?: "Android could not open the APK installer.")
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Install downloaded APK") }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.CheckCircle, null, tint = Forest)
                        Spacer(Modifier.width(10.dp))
                        Text("Camera analysis and workout history stay on this device. GitHub is contacted only when you check or download an update.", color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
            item {
                Text(
                    "${records.size} sessions saved on this device",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
