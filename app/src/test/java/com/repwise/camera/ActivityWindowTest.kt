package com.repwise.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityWindowTest {
    @Test
    fun activeTimeWaitsUntilPoseMovement() {
        assertFalse(
            ActivityWindow.isActive(
                ExerciseType.SQUAT,
                visible = true,
                goodForm = false,
                moved = false,
                lastMotionAt = 0,
                nowMs = 10_000,
            ),
        )
    }

    @Test
    fun activeTimeIncludesBriefRecoveryBetweenMovements() {
        assertTrue(
            ActivityWindow.isActive(
                ExerciseType.SQUAT,
                visible = true,
                goodForm = false,
                moved = false,
                lastMotionAt = 10_000,
                nowMs = 11_499,
            ),
        )
    }

    @Test
    fun activeTimeStopsWhenPoseMovementStops() {
        assertFalse(
            ActivityWindow.isActive(
                ExerciseType.SQUAT,
                visible = true,
                goodForm = false,
                moved = false,
                lastMotionAt = 10_000,
                nowMs = 11_501,
            ),
        )
    }

    @Test
    fun activeTimeStopsWhenPoseIsNotVisible() {
        assertFalse(
            ActivityWindow.isActive(
                ExerciseType.SQUAT,
                visible = false,
                goodForm = true,
                moved = true,
                lastMotionAt = 10_000,
                nowMs = 10_001,
            ),
        )
    }

    @Test
    fun plankCountsOnlyWhileAlignmentIsGood() {
        assertTrue(
            ActivityWindow.isActive(
                ExerciseType.PLANK,
                visible = true,
                goodForm = true,
                moved = false,
                lastMotionAt = 0,
                nowMs = 10_000,
            ),
        )
        assertFalse(
            ActivityWindow.isActive(
                ExerciseType.PLANK,
                visible = true,
                goodForm = false,
                moved = true,
                lastMotionAt = 10_000,
                nowMs = 10_001,
            ),
        )
    }
}
