package io.github.superisland.source.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LyricDocumentParserTest {
    @Test
    fun parsesLyricInfoTranslationAndMetadata() {
        val json = """
            {
              "songName": "Track",
              "artist": "Artist",
              "album": "Album",
              "songId": "42",
              "format": "lrc",
              "translation": "lrc",
              "lyric": "[00:01.00]hello\n[00:01.00]你好\n[00:03.00]world"
            }
        """.trimIndent()

        val document = LyricDocumentParser.parse(json)

        assertNotNull(document)
        assertEquals("Track", document?.title)
        assertEquals("Artist", document?.artist)
        assertEquals("Album", document?.album)
        assertEquals("42", document?.songId)
        assertEquals(2, document?.entries?.size)
        assertEquals("hello", document?.entries?.get(0)?.line?.text)
        assertEquals("你好", document?.entries?.get(0)?.translation?.text)
        assertEquals(3_000L, document?.entries?.get(0)?.line?.endMs)
        assertEquals(8_000L, document?.entries?.get(1)?.line?.endMs)
    }

    @Test
    fun parsesElrcWordTimingAndCompletesWordEnd() {
        val json = """
            {
              "format": "elrc",
              "lyric": "[00:01.00]<00:01.00>Hel<00:01.50>lo\n[00:03.00]<00:03.00>Bye"
            }
        """.trimIndent()

        val document = LyricDocumentParser.parse(json)
        val first = document?.entries?.firstOrNull()?.line

        assertNotNull(first)
        assertEquals("Hello", first?.text)
        assertEquals(2, first?.words?.size)
        assertEquals(1_000L, first?.words?.get(0)?.startMs)
        assertEquals(1_500L, first?.words?.get(0)?.endMs)
        assertEquals(2_000L, first?.words?.get(1)?.endMs)
        assertEquals(2_000L, first?.endMs)
    }

    @Test
    fun exposesNextTimedLineAsSecondary() {
        val document = LyricDocumentParser.parse(
            "[00:01.00]one\n[00:03.00]two\n[00:05.00]three",
        )
        assertNotNull(document)

        val snapshot = LyricResolver.snapshotForDocument(
            document = document!!,
            publisher = "player",
            playback = LyricPlayback(positionMs = 3_500L, isPlaying = true),
        )

        assertEquals("two", snapshot?.line?.text)
        assertEquals("three", snapshot?.secondary?.text)
        assertNull(snapshot?.translation)
    }
}
