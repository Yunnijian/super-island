package io.github.superisland.source.screenrecord

import org.junit.Assert.assertEquals
import org.junit.Test

class PausableRecordingClockTest {
    @Test
    fun excludesPausedIntervalsAndFreezesWhilePaused() {
        var now = 1_000L
        val clock = PausableRecordingClock { now }

        clock.start()
        now = 1_450L
        assertEquals(450L, clock.pause())

        now = 4_000L
        assertEquals(450L, clock.elapsed())
        assertEquals(450L, clock.resume())

        now = 4_275L
        assertEquals(725L, clock.elapsed())
    }

    @Test
    fun repeatedPauseAndResumeAreIdempotent() {
        var now = 0L
        val clock = PausableRecordingClock { now }

        clock.start()
        now = 100L
        assertEquals(100L, clock.pause())
        now = 250L
        assertEquals(100L, clock.pause())
        now = 400L
        assertEquals(100L, clock.resume())
        now = 450L
        assertEquals(150L, clock.resume())
    }

    @Test
    fun resetAllowsASeparateRecordingSession() {
        var now = 10L
        val clock = PausableRecordingClock { now }

        clock.start()
        now = 20L
        assertEquals(10L, clock.elapsed())
        clock.reset()

        now = 100L
        clock.start()
        now = 130L
        assertEquals(30L, clock.elapsed())
    }
}
