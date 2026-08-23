package io.github.superisland.source.lyric

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricPlaybackClockTest {
    @Test
    fun interpolatesOnlyWhilePlayingAndFreezesWhenPaused() {
        val clock = LyricPlaybackClock { 1_000L }
        clock.update(LyricPlayback(positionMs = 500, speed = 1f, isPlaying = true), 1_000)
        assertEquals(750, clock.positionAt(1_250))
        clock.update(LyricPlayback(positionMs = 750, speed = 1f, isPlaying = false), 1_300)
        assertEquals(750, clock.positionAt(2_000))
    }

    @Test
    fun clampsToDuration() {
        val clock = LyricPlaybackClock { 0L }
        clock.update(
            LyricPlayback(positionMs = 900, speed = 2f, isPlaying = true, durationMs = 1_000),
            0,
        )
        assertEquals(1_000, clock.positionAt(500))
    }
}
