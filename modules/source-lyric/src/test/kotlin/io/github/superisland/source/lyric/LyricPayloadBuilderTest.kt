package io.github.superisland.source.lyric

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricPayloadBuilderTest {
    private fun island(payload: String) = Json.parseToJsonElement(payload)
        .jsonObject.getValue("param_v2").jsonObject
        .getValue("param_island").jsonObject

    private fun slotTitle(payload: String, slot: String): String = island(payload)
        .getValue("bigIslandArea").jsonObject
        .getValue(slot).jsonObject
        .getValue("textInfo").jsonObject
        .getValue("title").jsonPrimitive.content

    @Test
    fun emitsAuditedNestedProgressShape() {
        val line = LyricLine("hello", 1_000L, 2_000L)
        val payload = Json.parseToJsonElement(
            LyricPayloadBuilder.buildFocusLyricJson(
                LyricSnapshot(
                    publisher = "player",
                    line = line,
                    playback = LyricPlayback(positionMs = 1_500L, isPlaying = true),
                ),
            ),
        ).jsonObject
        val paramV2 = payload.getValue("param_v2").jsonObject
        val island = paramV2.getValue("param_island").jsonObject
        val small = island.getValue("smallIslandArea").jsonObject
        val combine = small.getValue("combinePicInfo").jsonObject
        assertEquals("super_island_lyric", paramV2.getValue("business").jsonPrimitive.content)
        assertEquals(50, combine.getValue("progressInfo").jsonObject.getValue("progress").jsonPrimitive.int)
        assertFalse(payload.containsKey("island_param"))
    }

    @Test
    fun supportsTranslationAndEmptyPlaceholderFailClosed() {
        val snapshot = LyricSnapshot(
            publisher = "player",
            line = LyricLine("主行", 0L, 0L),
            translation = LyricLine("译文", 0L, 0L),
        )
        val payload = Json.parseToJsonElement(
            LyricPayloadBuilder.buildFocusLyricJson(snapshot, LyricIslandConfig(showProgress = false)),
        ).jsonObject
        val right = payload.getValue("param_v2").jsonObject
            .getValue("param_island").jsonObject
            .getValue("bigIslandArea").jsonObject
            .getValue("imageTextInfoRight").jsonObject
            .getValue("textInfo").jsonObject
        assertEquals("主行", right.getValue("title").jsonPrimitive.content)
        assertTrue(
            LyricPayloadBuilder.buildFocusLyricJson(
                LyricSnapshot("player"),
                LyricIslandConfig(placeholder = LyricPlaceholder.NONE),
            ).isEmpty(),
        )
    }

    @Test
    fun metadataOnlyLayoutPublishesWithoutLyricText() {
        val snapshot = LyricSnapshot(
            publisher = "player",
            title = "Song",
            artist = "Artist",
            playback = LyricPlayback(positionMs = 1_000L, durationMs = 120_000L),
        )
        val payload = LyricPayloadBuilder.buildFocusLyricJson(
            snapshot,
            LyricIslandConfig(
                contentLeft = IslandContentMode.MUSIC_INFO,
                contentRight = IslandContentMode.NONE,
                musicInfoFirstLine = LyricMusicInfoLayout.FIELD_TITLE,
                placeholder = LyricPlaceholder.NONE,
                showProgress = false,
            ),
        )

        assertTrue(payload.isNotEmpty())
        assertEquals("Song", slotTitle(payload, "imageTextInfoLeft"))
        assertEquals("", slotTitle(payload, "imageTextInfoRight"))
    }

    @Test
    fun selectedMetadataTitleStaysMetadataWhenTheOtherSlotShowsLyric() {
        val snapshot = LyricSnapshot(
            publisher = "player",
            line = LyricLine("歌词原文", 0L, 5_000L),
            title = "歌曲标题",
            artist = "歌手",
        )
        val payload = LyricPayloadBuilder.buildFocusLyricJson(
            snapshot,
            LyricIslandConfig(
                contentLeft = IslandContentMode.MUSIC_INFO,
                contentRight = IslandContentMode.LYRIC,
                musicInfoFirstLine = LyricMusicInfoLayout.FIELD_TITLE,
                placeholder = LyricPlaceholder.NONE,
                showProgress = false,
            ),
        )

        assertEquals("歌曲标题", slotTitle(payload, "imageTextInfoLeft"))
        assertEquals("歌词原文", slotTitle(payload, "imageTextInfoRight"))
    }

    @Test
    fun noneSlotsStayEmptyInsteadOfFallingBackToLyric() {
        val snapshot = LyricSnapshot(
            publisher = "player",
            line = LyricLine("原词", 0L, 1_000L),
        )
        val noSlots = LyricPayloadBuilder.buildFocusLyricJson(
            snapshot,
            LyricIslandConfig(
                contentLeft = IslandContentMode.NONE,
                contentRight = IslandContentMode.NONE,
                placeholder = LyricPlaceholder.NONE,
                showProgress = false,
            ),
        )
        assertTrue(noSlots.isEmpty())

        val rightLyric = LyricPayloadBuilder.buildFocusLyricJson(
            snapshot,
            LyricIslandConfig(
                contentLeft = IslandContentMode.NONE,
                contentRight = IslandContentMode.LYRIC,
                placeholder = LyricPlaceholder.NONE,
                showProgress = false,
            ),
        )
        assertEquals("", slotTitle(rightLyric, "imageTextInfoLeft"))
        assertEquals("原词", slotTitle(rightLyric, "imageTextInfoRight"))
    }

    @Test
    fun ordinaryModeKeepsIndependentDuplicateLyricSlots() {
        val snapshot = LyricSnapshot(
            publisher = "player",
            line = LyricLine("原词", 0L, 1_000L),
            secondary = LyricLine("下一句", 1_000L, 2_000L),
            title = "歌曲",
            artist = "歌手",
        )
        val payload = LyricPayloadBuilder.buildFocusLyricJson(
            snapshot,
            LyricIslandConfig(
                lyricMode = 0,
                contentLeft = IslandContentMode.LYRIC,
                contentRight = IslandContentMode.LYRIC,
                placeholder = LyricPlaceholder.NONE,
                showProgress = false,
            ),
        )
        assertEquals("原词", slotTitle(payload, "imageTextInfoLeft"))
        assertEquals("原词", slotTitle(payload, "imageTextInfoRight"))
    }

    @Test
    fun ordinaryModeNormalizationKeepsExplicitSlots() {
        val normalized = LyricIslandConfig(
            lyricMode = 0,
            contentLeft = IslandContentMode.LYRIC,
            contentRight = IslandContentMode.LYRIC,
        ).normalized()

        assertEquals(IslandContentMode.LYRIC, normalized.contentLeft)
        assertEquals(IslandContentMode.LYRIC, normalized.contentRight)
    }

    @Test
    fun translationModesMatchHyperLyricPrecedence() {
        val snapshot = LyricSnapshot(
            publisher = "player",
            line = LyricLine("原词", 0L, 1_000L),
            translation = LyricLine("译文", 0L, 1_000L),
        )
        fun config(transform: LyricIslandConfig.() -> LyricIslandConfig) = LyricIslandConfig(
            lyricMode = 1,
            contentLeft = IslandContentMode.LYRIC,
            contentRight = IslandContentMode.LYRIC,
            placeholder = LyricPlaceholder.NONE,
            showProgress = false,
        ).transform()

        assertEquals(
            "译文",
            LyricPayloadBuilder.contentFor(snapshot, config { copy(translationOnly = true) }, IslandContentMode.LYRIC, true),
        )
        assertEquals(
            "原词",
            LyricPayloadBuilder.contentFor(snapshot, config { copy(swapTranslation = true) }, IslandContentMode.LYRIC, false),
        )
        assertEquals(
            "原词",
            LyricPayloadBuilder.contentFor(
                snapshot,
                config { copy(disableTranslation = true, translationOnly = true) },
                IslandContentMode.LYRIC,
                true,
            ),
        )
    }

    @Test
    fun nextLyricPreviewOwnsSecondLineInBasicScope() {
        val snapshot = LyricSnapshot(
            publisher = "player",
            line = LyricLine("原词", 0L, 1_000L),
            translation = LyricLine("译文", 0L, 1_000L),
            secondary = LyricLine("下一句", 1_000L, 2_000L),
        )
        val nextConfig = LyricIslandConfig(
            lyricMode = 1,
            sourceMode = LyricSourceMode.LYRICON,
            contentLeft = IslandContentMode.LYRIC,
            contentRight = IslandContentMode.LYRIC,
            nextLyricLine = true,
            placeholder = LyricPlaceholder.NONE,
            showProgress = false,
        )
        assertEquals("原词", LyricPayloadBuilder.contentFor(snapshot, nextConfig, IslandContentMode.LYRIC, true))
        assertEquals("下一句", LyricPayloadBuilder.contentFor(snapshot, nextConfig, IslandContentMode.LYRIC, false))

        // Song-level automatic translation switching is intentionally outside the basic
        // Super Island feature set. A legacy value cannot displace the configured next line.
        val retiredAutoConfig = nextConfig.copy(autoSwitchTranslation = true)
        assertEquals("原词", LyricPayloadBuilder.contentFor(snapshot, retiredAutoConfig, IslandContentMode.LYRIC, true))
        assertEquals("下一句", LyricPayloadBuilder.contentFor(snapshot, retiredAutoConfig, IslandContentMode.LYRIC, false))
    }

    @Test
    fun metadataTimeUsesHoursAndRoundedPercent() {
        val long = LyricSnapshot(
            publisher = "player",
            playback = LyricPlayback(positionMs = 61_234L, durationMs = 3_661_234L),
        )
        val longText = LyricPayloadBuilder.contentFor(
            long,
            LyricIslandConfig(
                contentLeft = IslandContentMode.MUSIC_INFO,
                musicInfoFirstLine = "duration,elapsed,remaining,progress_percent",
                musicInfoSeparator = LyricMusicInfoLayout.SEPARATOR_COMMA,
                placeholder = LyricPlaceholder.NONE,
                showProgress = false,
            ),
            IslandContentMode.MUSIC_INFO,
            true,
        )
        assertEquals("01:01:01, 00:01:01, 01:00:00, 2%", longText)

        val short = LyricSnapshot(
            publisher = "player",
            playback = LyricPlayback(positionMs = 61_000L, durationMs = 123_000L),
        )
        val shortText = LyricPayloadBuilder.contentFor(
            short,
            LyricIslandConfig(
                contentLeft = IslandContentMode.MUSIC_INFO,
                musicInfoFirstLine = "duration,elapsed,progress_percent",
                musicInfoSeparator = LyricMusicInfoLayout.SEPARATOR_COMMA,
                placeholder = LyricPlaceholder.NONE,
                showProgress = false,
            ),
            IslandContentMode.MUSIC_INFO,
            true,
        )
        assertEquals("02:03, 01:01, 50%", shortText)
    }
}
