package io.github.superisland.source.screenrecord

import java.util.concurrent.atomic.AtomicLong

/** Drops resumed video output until a post-resume self-contained key frame reaches the worker. */
internal class ResumeKeyFrameGate {
    private val minimumPresentationTimeUs = AtomicLong(NOT_WAITING)

    fun awaitNextKeyFrameAtOrAfter(presentationTimeUs: Long) {
        require(presentationTimeUs >= 0L) { "Resume presentation time must not be negative" }
        minimumPresentationTimeUs.set(presentationTimeUs)
    }

    fun shouldDrop(
        paused: Boolean,
        keyFrame: Boolean,
        presentationTimeUs: Long,
    ): Boolean {
        if (paused) return true
        while (true) {
            val minimum = minimumPresentationTimeUs.get()
            if (minimum == NOT_WAITING) return false
            if (!keyFrame || presentationTimeUs < minimum) return true
            if (minimumPresentationTimeUs.compareAndSet(minimum, NOT_WAITING)) return false
        }
    }

    private companion object {
        const val NOT_WAITING = -1L
    }
}
