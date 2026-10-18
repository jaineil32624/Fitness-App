package com.repwise.camera

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.repwise.domain.ExerciseType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class LandmarkPoint(val x: Float, val y: Float)

data class PoseFeedback(
    val points: Map<Int, LandmarkPoint>,
    val message: String,
    val reps: Int,
    val visible: Boolean,
    val goodForm: Boolean,
    val active: Boolean = false,
    /** Upright analysis frame size, so overlays can match the preview's crop. */
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
) {
    companion object {
        val Searching = PoseFeedback(emptyMap(), "Position your full body in frame", 0, visible = false, goodForm = false)
    }
}

internal object ActivityWindow {
    fun isActive(
        exercise: ExerciseType,
        visible: Boolean,
        goodForm: Boolean,
        moved: Boolean,
        lastMotionAt: Long,
        nowMs: Long,
    ): Boolean {
        if (!visible) return false
        if (exercise == ExerciseType.PLANK) return goodForm
        if (moved) return true
        return lastMotionAt > 0 && nowMs - lastMotionAt <= 1_500
    }
}

internal object MotionSignal {
    fun hasMeaningfulChange(
        current: Map<String, Float>,
        previous: Map<String, Float>,
        thresholdDegrees: Float = 3f,
    ): Boolean = current.any { (feature, value) ->
        previous[feature]?.let { abs(value - it) >= thresholdDegrees } == true
    }
}

class PoseWorkoutAnalyzer(private val exercise: ExerciseType) {
    private var isDown = false
    private var isOpen = false
    private var lastAlternatingSide = 0
    private var lastMotionFeatures: Map<String, Float>? = null
    private var lastMotionSampleAt = 0L
    private var lastMotionAt = 0L
    private var lastTimedRepAt = 0L
    private var reps = 0

    /** Synchronized because a camera restart (rotation, lens flip) can briefly overlap two frame threads. */
    @Synchronized
    fun analyze(
        pose: Pose,
        nowMs: Long = System.currentTimeMillis(),
        imageWidth: Int = 640,
        imageHeight: Int = 480,
    ): PoseFeedback {
        // ML Kit always returns all 33 landmarks, guessing positions for joints it can't see.
        // Only trust joints it believes are actually in the frame.
        val trusted = pose.allPoseLandmarks.filter { it.inFrameLikelihood >= MIN_IN_FRAME_LIKELIHOOD }
        val trustedTypes = trusted.mapTo(HashSet()) { it.landmarkType }
        val points = trusted.associate { landmark ->
            landmark.landmarkType to LandmarkPoint(
                (landmark.position.x / imageWidth.coerceAtLeast(1)).coerceIn(0f, 1f),
                (landmark.position.y / imageHeight.coerceAtLeast(1)).coerceIn(0f, 1f),
            )
        }
        fun point(type: Int) = if (type in trustedTypes) pose.getPoseLandmark(type)?.position else null
        fun angle(a: Int, b: Int, c: Int): Double? {
            val first = point(a) ?: return null
            val middle = point(b) ?: return null
            val last = point(c) ?: return null
            val radians = atan2((last.y - middle.y).toDouble(), (last.x - middle.x).toDouble()) -
                atan2((first.y - middle.y).toDouble(), (first.x - middle.x).toDouble())
            return abs(Math.toDegrees(radians)).let { if (it > 180) 360 - it else it }
        }
        fun averageAngle(left: Double?, right: Double?): Double? =
            if (left != null && right != null) (left + right) / 2 else left ?: right

        val shoulderL = point(PoseLandmark.LEFT_SHOULDER)
        val shoulderR = point(PoseLandmark.RIGHT_SHOULDER)
        val hipL = point(PoseLandmark.LEFT_HIP)
        val hipR = point(PoseLandmark.RIGHT_HIP)
        val ankleL = point(PoseLandmark.LEFT_ANKLE)
        val ankleR = point(PoseLandmark.RIGHT_ANKLE)
        val wristL = point(PoseLandmark.LEFT_WRIST)
        val wristR = point(PoseLandmark.RIGHT_WRIST)
        val shoulderMidY = if (shoulderL != null && shoulderR != null) (shoulderL.y + shoulderR.y) / 2 else null
        val armRaised = shoulderMidY != null && wristL != null && wristR != null &&
            wristL.y < shoulderMidY && wristR.y < shoulderMidY
        val shoulderWidth = if (shoulderL != null && shoulderR != null) distance(shoulderL.x, shoulderL.y, shoulderR.x, shoulderR.y) else null
        val footWidth = if (ankleL != null && ankleR != null) distance(ankleL.x, ankleL.y, ankleR.x, ankleR.y) else null
        val legsWide = shoulderWidth != null && footWidth != null && footWidth > shoulderWidth * 1.45f
        val leftKneeAngle = angle(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
        val rightKneeAngle = angle(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        val kneeAngle = averageAngle(leftKneeAngle, rightKneeAngle)
        val elbowAngle = averageAngle(
            angle(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            angle(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
        )
        val hipAngle = averageAngle(
            angle(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE),
            angle(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE),
        )
        val shoulder = shoulderL ?: shoulderR
        val hip = hipL ?: hipR
        val ankle = ankleL ?: ankleR
        val bodyLineAngle = if (shoulder != null && hip != null && ankle != null) {
            abs(Math.toDegrees(atan2((ankle.y - shoulder.y).toDouble(), (ankle.x - shoulder.x).toDouble())))
                .let { minOf(it, abs(180 - it)) }
        } else null

        var feedback = "Hold still while we find your pose"
        var goodForm = false
        when (exercise) {
            ExerciseType.SQUAT -> {
                if (kneeAngle != null) {
                    feedback = when {
                        kneeAngle < 100 -> "Good depth — drive through your heels"
                        kneeAngle > 165 -> "Stand tall and brace your core"
                        else -> "Keep your chest up and lower with control"
                    }
                    goodForm = kneeAngle in 65.0..125.0
                    if (kneeAngle < 105) isDown = true
                    if (kneeAngle > 155 && isDown) { reps++; isDown = false }
                }
            }
            ExerciseType.LUNGE -> {
                val frontKneeAngle = listOfNotNull(leftKneeAngle, rightKneeAngle).minOrNull()
                if (frontKneeAngle != null) {
                    feedback = if (frontKneeAngle < 105) "Nice lunge — keep your front knee over your ankle" else "Step back and lower your hips with control"
                    goodForm = frontKneeAngle in 65.0..125.0
                    if (frontKneeAngle < 108) isDown = true
                    if (frontKneeAngle > 150 && isDown) { reps++; isDown = false }
                }
            }
            ExerciseType.PUSH_UP -> {
                if (elbowAngle != null) {
                    feedback = when {
                        bodyLineAngle != null && bodyLineAngle > 24 -> "Keep a straight line from shoulders to ankles"
                        elbowAngle < 95 -> "Strong depth — press the floor away"
                        else -> "Lower your chest with elbows at about 45 degrees"
                    }
                    goodForm = bodyLineAngle != null && bodyLineAngle < 22 && elbowAngle < 115
                    if (elbowAngle < 95) isDown = true
                    if (elbowAngle > 155 && isDown) { reps++; isDown = false }
                }
            }
            ExerciseType.JUMPING_JACK -> {
                val open = armRaised && legsWide
                feedback = if (open) "Reach high and land softly" else "Jump out, raise both arms, then return"
                goodForm = open
                if (open) isOpen = true
                if (!open && isOpen) { reps++; isOpen = false }
            }
            ExerciseType.PLANK -> {
                feedback = when {
                    bodyLineAngle == null -> "Turn sideways so we can check your alignment"
                    bodyLineAngle > 22 -> "Keep your hips in line with your shoulders"
                    else -> "Great alignment — keep breathing steadily"
                }
                goodForm = bodyLineAngle != null && bodyLineAngle < 22
                if (goodForm && nowMs - lastTimedRepAt >= 1_000) {
                    reps++
                    lastTimedRepAt = nowMs
                }
            }
            ExerciseType.CRUNCH -> {
                if (hipAngle != null) {
                    feedback = if (hipAngle < 100) "Squeeze at the top, and keep your neck relaxed"
                    else "Lift your shoulders with control; don't pull on your neck"
                    goodForm = hipAngle in 45.0..105.0
                    if (hipAngle < 85) isDown = true
                    if (hipAngle > 115 && isDown) { reps++; isDown = false }
                }
            }
            ExerciseType.SHOULDER_PRESS -> {
                if (elbowAngle != null && shoulderMidY != null && wristL != null && wristR != null) {
                    val pressedUp = armRaised
                    feedback = if (pressedUp) "Press overhead without arching your back"
                    else "Press both hands overhead with control"
                    goodForm = pressedUp
                    if (!pressedUp && elbowAngle < 125) isDown = true
                    if (pressedUp && isDown) { reps++; isDown = false }
                }
            }
            ExerciseType.MOUNTAIN_CLIMBER -> {
                val leftBent = (leftKneeAngle ?: 180.0) < 125
                val rightBent = (rightKneeAngle ?: 180.0) < 125
                val side = when {
                    leftBent && rightBent -> 0
                    leftBent -> 1
                    rightBent -> 2
                    else -> 0
                }
                feedback = if (side != 0) "Drive your knee forward; keep your shoulders stacked"
                else "Keep a strong plank and bring your knees forward one at a time"
                goodForm = bodyLineAngle != null && bodyLineAngle < 25 && side != 0
                if (side != 0 && side != lastAlternatingSide) {
                    reps++
                    lastAlternatingSide = side
                }
            }
            ExerciseType.GLUTE_BRIDGE -> {
                if (hipAngle != null) {
                    feedback = if (hipAngle > 150) "Squeeze your glutes at the top"
                    else "Drive through your heels and lift your hips"
                    goodForm = hipAngle > 145
                    if (hipAngle < 130) isDown = true
                    if (hipAngle > 150 && isDown) { reps++; isDown = false }
                }
            }
            ExerciseType.HIGH_KNEES -> {
                val leftAngle = leftKneeAngle
                val rightAngle = rightKneeAngle
                val side = when {
                    leftAngle != null && leftAngle < 135 && (rightAngle == null || leftAngle < rightAngle) -> 1
                    rightAngle != null && rightAngle < 135 -> 2
                    else -> 0
                }
                feedback = if (side != 0) "Nice drive — alternate knees and stay light on your feet"
                else "Jog in place and lift each knee toward hip height"
                goodForm = side != 0
                if (side != 0 && side != lastAlternatingSide) {
                    reps++
                    lastAlternatingSide = side
                }
            }
        }
        val visible = points.size >= 8
        val motionFeatures = buildMap {
            kneeAngle?.let { put("knee", it.toFloat()) }
            elbowAngle?.let { put("elbow", it.toFloat()) }
            hipAngle?.let { put("hip", it.toFloat()) }
            if (shoulderMidY != null && wristL != null && wristR != null) put("arms", if (armRaised) 100f else 0f)
            if (shoulderWidth != null && shoulderWidth > 0 && footWidth != null) {
                put("stance", (footWidth / shoulderWidth) * 100f)
            }
        }
        val previous = lastMotionFeatures
        val sampleMotion = nowMs - lastMotionSampleAt >= 250
        val moved = visible && sampleMotion && previous != null &&
            MotionSignal.hasMeaningfulChange(motionFeatures, previous)
        if (moved) lastMotionAt = nowMs
        if (visible && (previous == null || sampleMotion)) {
            lastMotionFeatures = motionFeatures
            lastMotionSampleAt = nowMs
        } else if (!visible) {
            lastMotionFeatures = null
            lastMotionSampleAt = 0
        }
        val active = ActivityWindow.isActive(
            exercise = exercise,
            visible = visible,
            goodForm = goodForm,
            moved = moved,
            lastMotionAt = lastMotionAt,
            nowMs = nowMs,
        )
        return PoseFeedback(points, feedback, reps, visible, goodForm, active, imageWidth, imageHeight)
    }

    private companion object {
        const val MIN_IN_FRAME_LIKELIHOOD = 0.5f
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
}
