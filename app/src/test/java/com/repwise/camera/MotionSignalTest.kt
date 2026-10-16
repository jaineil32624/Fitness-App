package com.repwise.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSignalTest {
    @Test
    fun ignoresSmallPoseJitter() {
        assertFalse(
            MotionSignal.hasMeaningfulChange(
                current = mapOf("knee" to 91f),
                previous = mapOf("knee" to 89f),
            ),
        )
    }

    @Test
    fun detectsDeliberateJointMovement() {
        assertTrue(
            MotionSignal.hasMeaningfulChange(
                current = mapOf("knee" to 98f),
                previous = mapOf("knee" to 89f),
            ),
        )
    }

    @Test
    fun ignoresFeaturesUnavailableInBothFrames() {
        assertFalse(MotionSignal.hasMeaningfulChange(emptyMap(), emptyMap()))
    }
}
