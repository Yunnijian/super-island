package io.github.superisland.source.lyric

import android.os.SystemClock

/**
 * Interpolates a player-reported position between Binder updates.
 *
 * The clock is deliberately based on elapsedRealtime rather than wall time. A seek, pause or
 * source switch replaces the anchor, so stale callbacks cannot make a line jump backwards.
 */
class LyricPlaybackClock @JvmOverloads constructor(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private var anchorPositionMs = 0L
    private var anchorElapsedMs = elapsedRealtime()
    private var speed = 1f
    private var active = false
    private var durationMs = 0L

    @Synchronized
    fun update(playback: LyricPlayback, nowMs: Long = elapsedRealtime()) {
        anchorPositionMs = playback.positionMs.coerceAtLeast(0L)
        anchorElapsedMs = nowMs
        speed = playback.speed.takeIf { it.isFinite() && it >= 0f } ?: 1f
        active = playback.isPlaying && speed > 0f
        durationMs = playback.durationMs.coerceAtLeast(0L)
    }

    @Synchronized
    fun positionAt(nowMs: Long = elapsedRealtime()): Long {
        val elapsed = (nowMs - anchorElapsedMs).coerceAtLeast(0L)
        val interpolated = if (active) {
            anchorPositionMs + (elapsed.toDouble() * speed.toDouble()).toLong()
        } else {
            anchorPositionMs
        }
        return if (durationMs > 0L) interpolated.coerceIn(0L, durationMs) else interpolated
    }

    @Synchronized
    fun isActive(): Boolean = active

    @Synchronized
    fun reset() {
        anchorPositionMs = 0L
        anchorElapsedMs = elapsedRealtime()
        speed = 1f
        active = false
        durationMs = 0L
    }
}
