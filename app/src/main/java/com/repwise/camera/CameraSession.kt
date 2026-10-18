package com.repwise.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Binds a camera preview plus pose analysis to [lifecycleOwner]. Frames are analyzed at a
 * modest resolution (pose detection doesn't benefit from more) to keep CPU and battery use low,
 * and only the latest frame is processed so the pipeline never queues up.
 */
class CameraSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val analyzer: PoseWorkoutAnalyzer,
    private val frontCamera: Boolean,
    private val onFeedback: (PoseFeedback) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * ML Kit can deliver a frame's result after [close] has shut [executor] down. Rejecting that
     * callback would crash the app, so late callbacks run inline instead; they only release the frame.
     */
    private val callbacks = Executor { task ->
        try {
            executor.execute(task)
        } catch (_: RejectedExecutionException) {
            task.run()
        }
    }
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
    )
    private val closed = AtomicBoolean(false)
    private val processing = AtomicBoolean(false)
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (closed.get()) return@addListener
            val cameraProvider = try {
                future.get()
            } catch (error: Exception) {
                Log.e(TAG, "Camera provider unavailable", error)
                onError("The camera couldn't start. Close other camera apps and try again.")
                return@addListener
            }
            provider = cameraProvider
            val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            if (!runCatching { cameraProvider.hasCamera(selector) }.getOrDefault(false)) {
                onError(if (frontCamera) "No front camera found. Try the rear camera." else "No rear camera found. Try the front camera.")
                return@addListener
            }
            val cameraPreview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, ::analyze) }
            preview = cameraPreview
            imageAnalysis = analysis
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, cameraPreview, analysis)
            } catch (error: Exception) {
                Log.e(TAG, "Camera bind failed", error)
                onError("Camera unavailable: ${error.localizedMessage ?: "check camera access"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun analyze(proxy: androidx.camera.core.ImageProxy) {
        val image = proxy.image
        if (image == null || closed.get() || !processing.compareAndSet(false, true)) {
            proxy.close()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(image, rotation)
        val rotated = rotation % 180 != 0
        val width = if (rotated) proxy.height else proxy.width
        val height = if (rotated) proxy.width else proxy.height
        detector.process(input)
            .addOnSuccessListener(callbacks) { pose ->
                if (closed.get()) return@addOnSuccessListener
                val result = analyzer.analyze(pose, imageWidth = width, imageHeight = height)
                ContextCompat.getMainExecutor(context).execute { if (!closed.get()) onFeedback(result) }
            }
            .addOnFailureListener(callbacks) { error -> Log.w(TAG, "Pose detection failed for a frame", error) }
            .addOnCompleteListener(callbacks) {
                processing.set(false)
                proxy.close()
            }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        provider?.let { cameraProvider -> listOfNotNull(preview, imageAnalysis).forEach { cameraProvider.unbind(it) } }
        imageAnalysis?.clearAnalyzer()
        detector.close()
        executor.shutdown()
    }

    private companion object {
        const val TAG = "CameraSession"
    }
}
