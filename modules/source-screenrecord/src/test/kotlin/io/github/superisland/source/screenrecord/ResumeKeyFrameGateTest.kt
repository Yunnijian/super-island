package io.github.superisland.source.screenrecord

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeKeyFrameGateTest {
    @Test
    fun resumeDropsDependentFramesUntilKeyFrame() {
        val gate = ResumeKeyFrameGate()

        assertFalse(gate.shouldDrop(paused = false, keyFrame = false, presentationTimeUs = 10L))
        gate.awaitNextKeyFrameAtOrAfter(20L)
        assertTrue(gate.shouldDrop(paused = false, keyFrame = false, presentationTimeUs = 20L))
        assertFalse(gate.shouldDrop(paused = false, keyFrame = true, presentationTimeUs = 21L))
        assertFalse(gate.shouldDrop(paused = false, keyFrame = false, presentationTimeUs = 22L))
    }

    @Test
    fun keyFrameObservedWhilePausedDoesNotOpenGate() {
        val gate = ResumeKeyFrameGate()

        gate.awaitNextKeyFrameAtOrAfter(20L)
        assertTrue(gate.shouldDrop(paused = true, keyFrame = true, presentationTimeUs = 20L))
        assertTrue(gate.shouldDrop(paused = false, keyFrame = false, presentationTimeUs = 21L))
        assertFalse(gate.shouldDrop(paused = false, keyFrame = true, presentationTimeUs = 22L))
    }

    @Test
    fun staleKeyFrameBeforeResumeBoundaryDoesNotOpenGate() {
        val gate = ResumeKeyFrameGate()

        gate.awaitNextKeyFrameAtOrAfter(1_000L)
        assertTrue(gate.shouldDrop(paused = false, keyFrame = true, presentationTimeUs = 999L))
        assertTrue(gate.shouldDrop(paused = false, keyFrame = false, presentationTimeUs = 1_000L))
        assertFalse(gate.shouldDrop(paused = false, keyFrame = true, presentationTimeUs = 1_001L))
    }
}
