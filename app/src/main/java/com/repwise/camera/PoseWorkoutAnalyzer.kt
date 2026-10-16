package com.repwise.camera

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

enum class ExerciseType(val title: String, val met: Float, val isTimed: Boolean = false) {
    SQUAT("Squats", 5.0f),
    PUSH_UP("Push-ups", 7.0f),
    JUMPING_JACK("Jumping jacks", 8.0f),
    LUNGE("Lunges", 5.0f),
    PLANK("Plank", 3.5f, true),
}

data class LandmarkPoint(val x: Float, val y: Float)

data class PoseFeedback(
    val points: Map<Int, LandmarkPoint>,
    val message: String,
    val reps: Int,
    val visible: Boolean,
    val goodForm: Boolean,
)

class PoseWorkoutAnalyzer(private val exercise: ExerciseType) {
    private var isDown = false
    private var isOpen = false
    private var lastTimedRepAt = 0L
    private var reps = 0

    fun analyze(
        pose: Pose,
        nowMs: Long = System.currentTimeMillis(),
        imageWidth: Int = 640,
        imageHeight: Int = 480,
    ): PoseFeedback {
        val points = pose.allPoseLandmarks.associate { landmark ->
            landmark.landmarkType to LandmarkPoint(
                (landmark.position.x / imageWidth.coerceAtLeast(1)).coerceIn(0f, 1f),
                (landmark.position.y / imageHeight.coerceAtLeast(1)).coerceIn(0f, 1f),
            )
        }
        fun point(type: Int) = pose.getPoseLandmark(type)?.position
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
        val kneeAngle = averageAngle(
            angle(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
            angle(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
        )
        val elbowAngle = averageAngle(
            angle(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            angle(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
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
                if (kneeAngle != null) {
                    feedback = if (kneeAngle < 105) "Nice lunge — keep your front knee over your ankle" else "Step back and lower your hips with control"
                    goodForm = kneeAngle in 65.0..125.0
                    if (kneeAngle < 108) isDown = true
                    if (kneeAngle > 150 && isDown) { reps++; isDown = false }
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
        }
        return PoseFeedback(points, feedback, reps, points.size >= 8, goodForm)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
}
