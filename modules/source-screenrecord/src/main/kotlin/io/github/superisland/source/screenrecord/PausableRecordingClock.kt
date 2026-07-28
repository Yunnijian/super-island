package io.github.superisland.source.screenrecord

/** Monotonic active-recording clock that excludes time spent paused. */
internal class PausableRecordingClock(
    private val now: () -> Long,
) {
    private val lock = Any()
    private var startedAt: Long? = null
    private var pausedAt: Long? = null
    private var accumulatedPaused = 0L

    fun start() {
        synchronized(lock) {
            check(startedAt == null) { "Recording clock is already started" }
            startedAt = now()
            pausedAt = null
            accumulatedPaused = 0L
        }
    }

    fun pause(): Long =
        synchronized(lock) {
            val current = now()
            checkNotNull(startedAt) { "Recording clock is not started" }
            if (pausedAt == null) pausedAt = current
            elapsedLocked(current)
        }

    fun resume(): Long =
        synchronized(lock) {
            val current = now()
            checkNotNull(startedAt) { "Recording clock is not started" }
            pausedAt?.let { pauseStart ->
                accumulatedPaused += (current - pauseStart).coerceAtLeast(0L)
                pausedAt = null
            }
            elapsedLocked(current)
        }

    fun elapsed(): Long = synchronized(lock) { elapsedLocked(now()) }

    fun reset() {
        synchronized(lock) {
            startedAt = null
            pausedAt = null
            accumulatedPaused = 0L
        }
    }

    private fun elapsedLocked(current: Long): Long {
        val start = checkNotNull(startedAt) { "Recording clock is not started" }
        val currentPause = pausedAt?.let { (current - it).coerceAtLeast(0L) } ?: 0L
        return (current - start - accumulatedPaused - currentPause).coerceAtLeast(0L)
    }
}
