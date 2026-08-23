package io.github.superisland.source.lyric

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesMultipleTagsAndWordTiming() {
        val lines = LrcParser.parseLrc("[00:01.20][00:02.00]<00:01.20>好<00:01.60>世界")
        assertEquals(2, lines.size)
        assertEquals(1_200L, lines[0].startMs)
        assertEquals("好世界", lines[0].text)
        assertEquals(1_600L, lines[0].words[0].endMs)
    }

    @Test
    fun ignoresMetadataAndBoundsInput() {
        assertEquals(0, LrcParser.parseLrc("[ar:artist]\n[ti:title]").size)
        assertEquals(null, LrcParser.parseLine("plain text"))
    }
}
