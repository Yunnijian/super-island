package io.github.superisland.source.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricIslandConfigCodecTest {
    @Test
    fun roundTripsRichConfig() {
        val config = LyricIslandConfig(
            enabled = true,
            sourceMode = LyricSourceMode.MEDIA_FALLBACK,
            slot = LyricSlot.RIGHT,
            animation = LyricAnimation.MARQUEE,
            widthMode = 1,
            dynamicWidthBasis = 1,
            rightContentMaxWidth = 180,
            dynamicMinWidth = 40,
            dynamicMaxWidth = 220,
            disableWidthLimit = true,
            leftPaddingLeft = 8,
            rightPaddingRight = 6,
            albumCoverStyle = 3,
            musicWaveStyle = 2,
            textSizeSp = 20f,
            displayTranslation = false,
            bold = true,
        )
        assertEquals(config.normalized(), LyricIslandConfigCodec.decode(LyricIslandConfigCodec.encode(config)))
    }

    @Test
    fun malformedOrOversizedConfigFailsClosed() {
        val decoded = LyricIslandConfigCodec.decode("{" + "x".repeat(5000) + "}")
        assertFalse(decoded.enabled)
        assertEquals(LyricAnimation.WORD_SYNC, decoded.animation)
    }

    @Test
    fun importsLatestHyperLyricKeysAndNormalizes() {
        val decoded = LyricIslandConfigCodec.decode(
            """
            {
              "schema": 2,
              "key_hook_island_content_right": 7,
              "key_hook_placeholder_format": 2,
              "key_hook_text_size_ratio": 0.85,
              "key_hook_font_weight": 720,
              "key_hook_font_italic": true,
              "key_hook_marquee_mode": true,
              "key_hook_marquee_speed": 55,
              "key_hook_marquee_delay": 800,
              "key_hook_marquee_metadata_mode": false,
              "key_hook_syllable_relative": false,
              "key_hook_word_motion_enabled": true,
              "key_hook_word_motion_latin_wave": 2.5,
              "key_hook_disable_translation": true
            }
            """.trimIndent(),
        )

        assertEquals(0.85f, decoded.textSizeRatio)
        assertEquals(IslandContentMode.LYRIC, decoded.contentRight)
        assertEquals(LyricPlaceholder.NAME, decoded.placeholder)
        assertEquals(720, decoded.fontWeight)
        assertTrue(decoded.fontItalic)
        assertTrue(decoded.marqueeMode)
        assertEquals(55, decoded.marqueeSpeed)
        assertEquals(800, decoded.marqueeDelay)
        assertFalse(decoded.metadataMarqueeMode)
        assertFalse(decoded.syllableRelative)
        assertTrue(decoded.wordMotionEnabled)
        assertEquals(2.5f, decoded.wordMotionLatinWave)
        assertFalse(decoded.displayTranslation)
    }

    @Test
    fun roundTripsHyperLyricMediaCardContract() {
        val config = LyricIslandConfig(
            mediaCard = MediaCardConfig(
                removeIslandWhitelist = true,
                notification = NotificationMediaCardConfig(
                    cardSwitcherEnabled = true,
                    cardSwitcherMode = MediaCardConstants.CARD_SWITCHER_SINGLE,
                    cardSwitcherMaxCount = 6,
                    layoutStyle = MediaCardConstants.LAYOUT_PIXEL,
                    ambientFlowMode = MediaCardConstants.NOTIFICATION_AMBIENT_CUSTOM_FULL,
                    cardTheme = MediaCardConstants.THEME_ALWAYS_LIGHT,
                    coverStyle = MediaCardConstants.COVER_ROTATING_CIRCLE,
                    progressStyle = MediaCardConstants.PROGRESS_WAVE,
                    progressHeadGlow = true,
                    thumbStyle = MediaCardConstants.THUMB_VERTICAL,
                    hideCoverSource = true,
                    hideCoverShadow = true,
                    disableCoverFlip = true,
                    hideDeviceSwitch = true,
                    hideCustomActions = true,
                    hideTime = true,
                    actionAlignLeft = true,
                    actionOrder = MediaCardConstants.ACTION_ORDER_PLAY_LEFT,
                    backgroundStyle = MediaCardConstants.BACKGROUND_SOFT_COVER,
                    backgroundBlur = 20,
                    backgroundColorAnimation = true,
                    backgroundAutoInvert = true,
                    softCoverTone = MediaCardConstants.SOFT_COVER_FOLLOW_SYSTEM,
                ),
                islandExpanded = IslandExpandedMediaCardConfig(
                    layoutStyle = MediaCardConstants.LAYOUT_IOS,
                    ambientFlowMode = MediaCardConstants.ISLAND_EXPANDED_AMBIENT_COVER_COLOR,
                    cardTheme = MediaCardConstants.THEME_ALWAYS_DARK,
                    coverStyle = MediaCardConstants.COVER_CIRCLE,
                    progressStyle = MediaCardConstants.PROGRESS_WAVE,
                    progressHeadGlow = false,
                    thumbStyle = MediaCardConstants.THUMB_HIDDEN,
                    hideCoverSource = true,
                    disableCoverFlip = true,
                    hideDeviceSwitch = true,
                    hideCustomActions = true,
                    hideTime = true,
                    actionAlignLeft = true,
                    actionOrder = MediaCardConstants.ACTION_ORDER_CUSTOM_RIGHT,
                    backgroundStyle = MediaCardConstants.BACKGROUND_LINEAR_GRADIENT,
                    backgroundBlur = 1,
                    backgroundColorAnimation = true,
                    backgroundAutoInvert = true,
                    softCoverTone = MediaCardConstants.SOFT_COVER_LIGHT,
                ),
                alwaysOnDisplay = AlwaysOnDisplayMediaCardConfig(
                    disableMediaCardCollapsing = true,
                ),
            ),
        )

        val encoded = LyricIslandConfigCodec.encode(config)

        assertEquals(config.normalized(), LyricIslandConfigCodec.decode(encoded))
        assertTrue(encoded.contains("\"removeIslandWhitelist\":true"))
        assertTrue(encoded.contains("\"islandExpanded\""))
    }

    @Test
    fun importsFlatHyperLyricMediaCardKeysWithRuntimeNormalization() {
        val decoded = LyricIslandConfigCodec.decode(
            """
            {
              "schema": 2,
              "key_hook_remove_island_whitelist": true,
              "key_hook_notification_media_card_switcher_enabled": true,
              "key_hook_notification_media_card_switcher_mode": 99,
              "key_hook_notification_media_card_switcher_max_count": 99,
              "key_hook_notification_media_layout_style": 99,
              "key_hook_notification_media_ambient_flow_mode": -1,
              "key_hook_notification_media_progress_style": 2,
              "key_hook_notification_media_background_blur": 99,
              "key_hook_notification_media_soft_cover_tone": 99,
              "key_hook_island_expanded_media_layout_style": 1,
              "key_hook_island_expanded_media_ambient_flow_mode": 1,
              "key_hook_island_expanded_media_progress_style": 99,
              "key_hook_island_expanded_media_background_blur": -1,
              "key_hook_aod_disable_media_card_collapsing": true
            }
            """.trimIndent(),
        )

        val notification = decoded.mediaCard.notification
        assertTrue(decoded.mediaCard.removeIslandWhitelist)
        assertTrue(notification.cardSwitcherEnabled)
        assertEquals(MediaCardConstants.CARD_SWITCHER_MULTI, notification.cardSwitcherMode)
        assertEquals(MediaCardConstants.CARD_SWITCHER_MAX_COUNT, notification.cardSwitcherMaxCount)
        assertEquals(MediaCardConstants.LAYOUT_SYSTEM, notification.layoutStyle)
        assertEquals(MediaCardConstants.NOTIFICATION_AMBIENT_DISABLED, notification.ambientFlowMode)
        assertEquals(MediaCardConstants.PROGRESS_DEFAULT, notification.progressStyle)
        assertTrue(notification.progressHeadGlow)
        assertEquals(20, notification.backgroundBlur)
        assertEquals(MediaCardConstants.SOFT_COVER_FOLLOW_SYSTEM, notification.softCoverTone)
        assertEquals(MediaCardConstants.LAYOUT_IOS, decoded.mediaCard.islandExpanded.layoutStyle)
        assertEquals(
            MediaCardConstants.ISLAND_EXPANDED_AMBIENT_DISABLED,
            decoded.mediaCard.islandExpanded.ambientFlowMode,
        )
        assertEquals(MediaCardConstants.PROGRESS_WAVE, decoded.mediaCard.islandExpanded.progressStyle)
        assertEquals(1, decoded.mediaCard.islandExpanded.backgroundBlur)
        assertTrue(decoded.mediaCard.alwaysOnDisplay.disableMediaCardCollapsing)
    }

    @Test
    fun encodesHyperLyricWireValuesAndProviderIds() {
        val encoded = LyricIslandConfigCodec.encode(
            LyricIslandConfig(
                sourceMode = LyricSourceMode.SUPER_LYRIC,
                contentLeft = IslandContentMode.MUSIC_INFO,
                contentRight = IslandContentMode.LYRIC,
                placeholder = LyricPlaceholder.COUNTDOWN,
            ),
        )

        val decoded = LyricIslandConfigCodec.decode(encoded)
        assertEquals(LyricSourceMode.SUPER_LYRIC, decoded.sourceMode)
        assertEquals(IslandContentMode.MUSIC_INFO, decoded.contentLeft)
        assertEquals(IslandContentMode.LYRIC, decoded.contentRight)
        assertEquals(LyricPlaceholder.COUNTDOWN, decoded.placeholder)
        assertTrue(encoded.contains("\"source\":\"superlyric\""))
        assertTrue(encoded.contains("\"contentLeft\":8"))
        assertTrue(encoded.contains("\"contentRight\":7"))
        assertTrue(encoded.contains("\"placeholder\":3"))
    }

    @Test
    fun importsProviderAliasesAndOrderedMusicInfoFields() {
        val decoded = LyricIslandConfigCodec.decode(
            """
            {
              "key_hook_lyric_source": "lyricinfo",
              "key_hook_island_content_left": 8,
              "key_hook_island_content_right": "lyric",
              "key_hook_island_music_info_first_line": "title,artist,title,unknown,progress_percent",
              "key_hook_island_music_info_second_line": "elapsed,remaining,progress_percent",
              "key_hook_island_music_info_separator": "slash"
            }
            """.trimIndent(),
        )

        assertEquals(LyricSourceMode.LYRIC_INFO, decoded.sourceMode)
        assertEquals(IslandContentMode.MUSIC_INFO, decoded.contentLeft)
        assertEquals(IslandContentMode.LYRIC, decoded.contentRight)
        assertEquals("title,artist,progress_percent", decoded.musicInfoFirstLine)
        assertEquals("elapsed,remaining,progress_percent", decoded.musicInfoSecondLine)
        assertEquals(LyricMusicInfoLayout.SEPARATOR_SLASH, decoded.musicInfoSeparator)
    }

    @Test
    fun importsFlatHyperLyricProviderDelayKeys() {
        val decoded = LyricIslandConfigCodec.decode(
            """
            {
              "schema": 2,
              "key_hook_lyricon_provider_delay_default": 250,
              "key_hook_lyricon_provider_delay_com.example.provider": -400
            }
            """.trimIndent(),
        )

        assertEquals(250, decoded.lyriconProviderDelayMs)
        assertEquals(-400, decoded.lyriconProviderDelays["com.example.provider"])
    }

    @Test
    fun preservesExplicitEmptyMusicInfoRows() {
        val normalized = LyricIslandConfig(
            musicInfoFirstLine = "unknown,",
            musicInfoSecondLine = "",
            musicInfoSeparator = "invalid",
        ).normalized()

        assertEquals("", normalized.musicInfoFirstLine)
        assertEquals("", normalized.musicInfoSecondLine)
        assertEquals(LyricMusicInfoLayout.SEPARATOR_HYPHEN, normalized.musicInfoSeparator)
    }

    @Test
    fun omittedMusicInfoRowsUseHyperLyricDefaults() {
        val decoded = LyricIslandConfigCodec.decode("{\"schema\":2}")

        assertEquals(LyricMusicInfoLayout.FIELD_TITLE, decoded.musicInfoFirstLine)
        assertEquals(LyricMusicInfoLayout.FIELD_ARTIST, decoded.musicInfoSecondLine)
    }

    @Test
    fun freshConfigUsesHyperLyricMarqueeAndWordMotionDefaults() {
        val config = LyricIslandConfig()

        assertEquals(0.7f, config.textSizeRatio)
        assertEquals(15, config.fadingEdgeLengthDp)
        assertEquals(LyricPlaceholder.COUNTDOWN, config.placeholder)
        assertEquals(30, config.marqueeSpeed)
        assertEquals(1500, config.marqueeDelay)
        assertEquals(1000, config.marqueeLoopDelay)
        assertFalse(config.marqueeInfinite)
        assertTrue(config.marqueeStopEnd)
        assertEquals(10, config.metadataMarqueeSpeed)
        assertEquals(4000, config.metadataMarqueeDelay)
        assertEquals(5000, config.metadataMarqueeLoopDelay)
        assertTrue(config.metadataMarqueeInfinite)
        assertTrue(config.syllableRelative)
        assertFalse(config.wordMotionEnabled)
        assertEquals(0.08f, config.wordMotionLatinLift)
        assertEquals(2.0f, config.wordMotionLatinWave)
    }

    @Test
    fun normalizesSimulatedLineDisplayWithUpstreamSyllablePolicy() {
        val normalized = LyricIslandConfig(
            syllableRelative = true,
            syllableHighlight = true,
            syllableLineDisplay = true,
            disableTranslation = false,
            displayTranslation = false,
            autoSwitchTranslation = true,
        ).normalized()

        assertFalse(normalized.syllableRelative)
        assertTrue(normalized.syllableHighlight)
        assertTrue(normalized.syllableLineDisplay)
        assertTrue(normalized.displayTranslation)
        assertFalse(normalized.autoSwitchTranslation)
    }

    @Test
    fun legacyDisplayTranslationCannotOverridePublicTranslationSwitch() {
        val enabled = LyricIslandConfigCodec.decode(
            "{\"schema\":2,\"disableTranslation\":false,\"displayTranslation\":false}",
        )
        val disabled = LyricIslandConfigCodec.decode(
            "{\"schema\":2,\"disableTranslation\":true,\"displayTranslation\":true}",
        )

        assertTrue(enabled.displayTranslation)
        assertFalse(disabled.displayTranslation)
    }

    @Test
    fun coverPickerOrderKeepsWireValuesStable() {
        assertEquals(listOf(0, 1, 3, 2, 4), LyricAlbumCoverStyle.pickerValues)
        assertEquals(2, LyricAlbumCoverStyle.pickerValues[3])
        assertEquals(3, LyricAlbumCoverStyle.pickerValues[2])
        assertEquals(3, LyricAlbumCoverStyle.pickerIndex(2))
    }

    @Test
    fun schemaOneUsesLegacyDefaultsWhenFieldsAreAbsent() {
        val decoded = LyricIslandConfigCodec.decode("{\"schema\":1}")

        assertEquals(LyricSourceMode.SUPER_LYRIC, decoded.sourceMode)
        assertEquals(14f, decoded.textSizeSp)
        assertEquals(0xFFFF5722.toInt(), decoded.highlightColor)
        assertEquals(10, decoded.fadingEdgeLengthDp)
        assertEquals(LyricPlaceholder.NAME_ARTIST, decoded.placeholder)
        assertEquals(11f, decoded.secondaryTextSizeSp)
    }

    @Test
    fun appliesHyperLyricWidthBoundsAndStyleWireValues() {
        val normalized = LyricIslandConfig(
            albumCoverStyle = 2,
            musicWaveStyle = 0,
            rightContentMaxWidth = 200,
            dynamicMinWidth = 1,
            dynamicMaxWidth = 200,
        ).normalized()

        assertEquals(2, normalized.albumCoverStyle)
        assertEquals(98, normalized.rightContentMaxWidth)
        assertEquals(22, normalized.dynamicMinWidth)
        assertEquals(98, normalized.dynamicMaxWidth)
        assertEquals(120, LyricIslandWidthPolicy.maxIslandWidth(showRhythm = false, disableWidthLimit = true))
        assertEquals(
            120,
            io.github.superisland.source.lyric.hyperlyric.common.SuperIslandWidthPolicy
                .maxIslandWidth(showRhythm = false, disableWidthLimit = true),
        )

        val wordMotion = LyricIslandConfig(
            wordMotionCjkWave = 20f,
            wordMotionLatinWave = 20f,
        ).normalized()
        assertEquals(10f, wordMotion.wordMotionCjkWave)
        assertEquals(10f, wordMotion.wordMotionLatinWave)
    }

    @Test
    fun dynamicWidthUsesHyperLyricLeftCompensation() {
        assertEquals(-22, LyricIslandWidthPolicy.leftContentWidthOffsetDp(
            showAlbum = true,
            showRhythm = false,
        ))
        assertEquals(122f, LyricIslandWidthPolicy.baseWidthFromLeftContentWidth(
            leftContentWidthDp = 100f,
            showAlbum = true,
            showRhythm = false,
        ))
    }
}
