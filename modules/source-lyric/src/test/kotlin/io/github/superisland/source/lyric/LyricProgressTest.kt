package io.github.superisland.source.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricProgressTest {
    @Test
    fun lineAndWordProgressAreBounded() {
        val line = LyricLine(
            text = "hello",
            startMs = 1_000,
            endMs = 2_000,
            words = listOf(
                LyricWord("he", 1_000, 1_400),
                LyricWord("llo", 1_400, 2_000),
            ),
        )
        assertEquals(0, LyricProgress.lineProgress(line, 0))
        assertEquals(50, LyricProgress.lineProgress(line, 1_500))
        assertEquals(100, LyricProgress.lineProgress(line, 3_000))
        assertEquals(0.5f, LyricProgress.wordProgress(line, 1_500))
        assertEquals(1, LyricProgress.activeWordIndex(line, 1_500))
    }

    @Test
    fun invalidTimingReturnsNoProgress() {
        val line = LyricLine("x", 0, 0)
        assertNull(LyricProgress.lineProgress(line, 0))
        assertNull(LyricProgress.wordProgress(line, 0))
    }

    @Test
    fun boundedTextRespectsCodePoints() {
        val value = "😀😀😀"
        assertEquals("😀😀", LyricProgress.boundedText(value, 2))
        assertTrue(LyricProgress.boundedText(" ", 2).isEmpty())
    }
}
