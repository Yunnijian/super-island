package io.github.superisland.source.lyric

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Versioned app <-> SystemUI configuration contract. */
object LyricIslandConfigCodec {
    private const val SCHEMA = 2
    private const val MAX_JSON_LENGTH = 16_384
    private val parser = Json { ignoreUnknownKeys = true }

    fun encode(config: LyricIslandConfig): String {
        val v = config.normalized()
        return buildJsonObject {
            put("schema", SCHEMA)
            put("enabled", v.enabled)
            put("mediaCard", encodeMediaCard(v.mediaCard))
            // HyperLyric persists provider ids (lyricon/superlyric/lyricinfo), not Kotlin enum
            // names. Keep the wire representation importable by the upstream hook.
            put("source", v.sourceMode.wireValue)
            put("lyriconProviderDelayMs", v.lyriconProviderDelayMs)
            put("lyriconProviderDelays", buildJsonObject {
                v.lyriconProviderDelays.forEach { (packageName, delay) -> put(packageName, delay) }
            })
            put("lyricMode", v.lyricMode)
            put("contentLeft", v.contentLeft.wireValue)
            put("contentRight", v.contentRight.wireValue)
            put("musicInfoFirstLine", v.musicInfoFirstLine)
            put("musicInfoSecondLine", v.musicInfoSecondLine)
            put("musicInfoSeparator", v.musicInfoSeparator)
            put("centerMusicInfo", v.centerMusicInfo)
            put("centerLyric", v.centerLyric)
            put("rightLyric", v.rightLyric)
            put("widthMode", v.widthMode)
            put("dynamicWidthBasis", v.dynamicWidthBasis)
            put("rightContentMaxWidth", v.rightContentMaxWidth)
            put("dynamicMinWidth", v.dynamicMinWidth)
            put("dynamicMaxWidth", v.dynamicMaxWidth)
            put("disableWidthLimit", v.disableWidthLimit)
            put("leftPaddingLeft", v.leftPaddingLeft)
            put("leftPaddingRight", v.leftPaddingRight)
            put("rightPaddingLeft", v.rightPaddingLeft)
            put("rightPaddingRight", v.rightPaddingRight)
            put("albumCoverStyle", v.albumCoverStyle)
            put("musicWaveStyle", v.musicWaveStyle)
            put("slot", v.slot.name)
            put("textSizeSp", v.textSizeSp)
            put("textSizeRatio", v.textSizeRatio)
            put("textColorStyle", v.textColorStyle)
            put("textColor", v.textColor)
            put("highlightColor", v.highlightColor)
            put("customFontPath", v.customFontPath)
            put("fontWeight", v.fontWeight)
            put("fontItalic", v.fontItalic)
            put("narrowLatinFont", v.narrowLatinFont)
            put("fadingEdgeLengthDp", v.fadingEdgeLengthDp)
            put("gradientProgressStyle", v.gradientProgressStyle)
            put("placeholder", v.placeholder.wireValue)
            put("marqueeMode", v.marqueeMode)
            put("marqueeSpeed", v.marqueeSpeed)
            put("marqueeDelay", v.marqueeDelay)
            put("marqueeLoopDelay", v.marqueeLoopDelay)
            put("marqueeInfinite", v.marqueeInfinite)
            put("marqueeStopEnd", v.marqueeStopEnd)
            put("metadataMarqueeMode", v.metadataMarqueeMode)
            put("metadataMarqueeSpeed", v.metadataMarqueeSpeed)
            put("metadataMarqueeDelay", v.metadataMarqueeDelay)
            put("metadataMarqueeLoopDelay", v.metadataMarqueeLoopDelay)
            put("metadataMarqueeInfinite", v.metadataMarqueeInfinite)
            put("syllableRelative", v.syllableRelative)
            put("syllableHighlight", v.syllableHighlight)
            put("syllableLineDisplay", v.syllableLineDisplay)
            put("wordMotionEnabled", v.wordMotionEnabled)
            put("wordMotionLatinByCharacter", v.wordMotionLatinByCharacter)
            put("wordMotionCjkLift", v.wordMotionCjkLift)
            put("wordMotionCjkWave", v.wordMotionCjkWave)
            put("wordMotionLatinLift", v.wordMotionLatinLift)
            put("wordMotionLatinWave", v.wordMotionLatinWave)
            put("disableTranslation", v.disableTranslation)
            put("translationOnly", v.translationOnly)
            put("swapTranslation", v.swapTranslation)
            put("nextLyricLine", v.nextLyricLine)
            put("autoSwitchTranslation", v.autoSwitchTranslation)
            put("animation", v.animation.name)
            put("animEnabled", v.animEnabled)
            put("animId", v.animId)
            // Compatibility payload fields
            put("showProgress", v.showProgress)
            put("secondaryTextSizeSp", v.secondaryTextSizeSp)
            put("secondaryTextColor", v.secondaryTextColor)
            put("displayTranslation", v.displayTranslation)
            put("bold", v.bold)
        }.toString()
    }

    fun decode(raw: String?): LyricIslandConfig {
        if (raw.isNullOrBlank() || raw.length > MAX_JSON_LENGTH) return LyricIslandConfig()
        return runCatching {
            val json = parser.parseToJsonElement(raw).jsonObject
            val schema = json.int(
                "schema",
                if (json.keys.any { it.startsWith("key_hook_") }) 2 else 1,
            )
            require(schema in 1..SCHEMA)
            val defaults = if (schema == 1) legacyDefaults() else LyricIslandConfig()
            val mediaCard = decodeMediaCard(json, defaults.mediaCard)
            val source = sourceModeOrDefault(
                json.string("source", "key_hook_lyric_source"),
                defaults.sourceMode,
            )
            val disableTranslation = json.boolean(
                "disableTranslation",
                defaults.disableTranslation,
                "key_hook_disable_translation",
            )
            val displayTranslation = if (json.value("displayTranslation") != null) {
                json.boolean("displayTranslation", defaults.displayTranslation)
            } else {
                !disableTranslation
            }
            val providerDelays = json.objectIntMap("lyriconProviderDelays").toMutableMap().apply {
                // HyperLyric stores per-provider offsets as flat preference keys. Preserve those
                // keys when importing an exported preference document alongside the new map form.
                json.entries.asSequence()
                    .filter { (key, value) ->
                        key.startsWith("key_hook_lyricon_provider_delay_") &&
                            value is JsonPrimitive && value.intOrNull != null
                    }
                    .take(64)
                    .forEach { (key, value) ->
                        val packageName = key.removePrefix("key_hook_lyricon_provider_delay_")
                        if (packageName.isNotBlank()) {
                            (value as JsonPrimitive).intOrNull?.let { put(packageName, it) }
                        }
                    }
            }
            LyricIslandConfig(
                enabled = json.boolean("enabled", defaults.enabled),
                mediaCard = mediaCard,
                sourceMode = source,
                lyriconProviderDelayMs = json.int("lyriconProviderDelayMs", defaults.lyriconProviderDelayMs, "key_hook_lyricon_provider_delay_default"),
                lyriconProviderDelays = providerDelays,
                lyricMode = json.int("lyricMode", defaults.lyricMode, "key_hook_lyric_mode"),
                contentLeft = contentModeOrDefault(
                    json.value("contentLeft", "key_hook_island_content_left"),
                    defaults.contentLeft,
                ),
                contentRight = contentModeOrDefault(
                    json.value("contentRight", "key_hook_island_content_right"),
                    defaults.contentRight,
                ),
                musicInfoFirstLine = LyricMusicInfoLayout.normalizeFields(
                    json.value("musicInfoFirstLine", "key_hook_island_music_info_first_line")?.contentOrNull,
                    defaults.musicInfoFirstLine,
                ),
                musicInfoSecondLine = LyricMusicInfoLayout.normalizeFields(
                    json.value("musicInfoSecondLine", "key_hook_island_music_info_second_line")?.contentOrNull,
                    defaults.musicInfoSecondLine,
                ),
                musicInfoSeparator = LyricMusicInfoLayout.normalizeSeparator(
                    json.string("musicInfoSeparator", "key_hook_island_music_info_separator")
                        .ifBlank { defaults.musicInfoSeparator },
                ),
                centerMusicInfo = json.boolean("centerMusicInfo", defaults.centerMusicInfo, "key_hook_center_music_info"),
                centerLyric = json.boolean("centerLyric", defaults.centerLyric, "key_hook_center_lyric"),
                rightLyric = json.boolean("rightLyric", defaults.rightLyric, "key_hook_right_lyric"),
                widthMode = json.int("widthMode", defaults.widthMode, "key_hook_island_width_mode"),
                dynamicWidthBasis = json.int("dynamicWidthBasis", defaults.dynamicWidthBasis, "key_hook_island_dynamic_width_basis"),
                rightContentMaxWidth = json.int("rightContentMaxWidth", defaults.rightContentMaxWidth, "key_hook_island_right_content_max_width"),
                dynamicMinWidth = json.int("dynamicMinWidth", defaults.dynamicMinWidth, "key_hook_island_dynamic_min_width"),
                dynamicMaxWidth = json.int("dynamicMaxWidth", defaults.dynamicMaxWidth, "key_hook_island_dynamic_max_width"),
                disableWidthLimit = json.boolean("disableWidthLimit", defaults.disableWidthLimit, "key_hook_island_disable_width_limit"),
                leftPaddingLeft = json.int("leftPaddingLeft", defaults.leftPaddingLeft, "key_hook_island_left_padding_left"),
                leftPaddingRight = json.int("leftPaddingRight", defaults.leftPaddingRight, "key_hook_island_left_padding_right"),
                rightPaddingLeft = json.int("rightPaddingLeft", defaults.rightPaddingLeft, "key_hook_island_right_padding_left"),
                rightPaddingRight = json.int("rightPaddingRight", defaults.rightPaddingRight, "key_hook_island_right_padding_right"),
                albumCoverStyle = json.int("albumCoverStyle", defaults.albumCoverStyle, "key_hook_island_album_cover_style"),
                musicWaveStyle = json.int("musicWaveStyle", defaults.musicWaveStyle, "key_hook_island_music_wave_style"),
                slot = enumOrDefault(json.string("slot"), defaults.slot),
                textSizeSp = json.float("textSizeSp", defaults.textSizeSp, "key_hook_text_size"),
                textSizeRatio = json.float("textSizeRatio", defaults.textSizeRatio, "key_hook_text_size_ratio"),
                textColorStyle = json.int("textColorStyle", defaults.textColorStyle, "key_hook_text_color_style"),
                textColor = json.int("textColor", defaults.textColor),
                highlightColor = json.int("highlightColor", defaults.highlightColor),
                customFontPath = json.string("customFontPath", "key_hook_custom_font_path"),
                fontWeight = json.int("fontWeight", defaults.fontWeight, "key_hook_font_weight"),
                fontItalic = json.boolean("fontItalic", defaults.fontItalic, "key_hook_font_italic"),
                narrowLatinFont = json.boolean("narrowLatinFont", defaults.narrowLatinFont, "key_hook_narrow_latin_font"),
                fadingEdgeLengthDp = json.int("fadingEdgeLengthDp", defaults.fadingEdgeLengthDp, "key_hook_fading_edge_length"),
                gradientProgressStyle = json.boolean("gradientProgressStyle", defaults.gradientProgressStyle, "key_hook_gradient_progress"),
                placeholder = placeholderOrDefault(
                    json.value("placeholder", "key_hook_placeholder_format"),
                    defaults.placeholder,
                ),
                marqueeMode = json.boolean(
                    "marqueeMode",
                    json.boolean("legacyMarquee", defaults.marqueeMode),
                    "key_hook_marquee_mode",
                ),
                marqueeSpeed = json.int("marqueeSpeed", defaults.marqueeSpeed, "key_hook_marquee_speed"),
                marqueeDelay = json.int("marqueeDelay", defaults.marqueeDelay, "key_hook_marquee_delay"),
                marqueeLoopDelay = json.int("marqueeLoopDelay", defaults.marqueeLoopDelay, "key_hook_marquee_loop_delay"),
                marqueeInfinite = json.boolean("marqueeInfinite", defaults.marqueeInfinite, "key_hook_marquee_infinite"),
                marqueeStopEnd = json.boolean("marqueeStopEnd", defaults.marqueeStopEnd, "key_hook_marquee_stop_end"),
                metadataMarqueeMode = json.boolean("metadataMarqueeMode", defaults.metadataMarqueeMode, "key_hook_marquee_metadata_mode"),
                metadataMarqueeSpeed = json.int("metadataMarqueeSpeed", defaults.metadataMarqueeSpeed, "key_hook_marquee_metadata_speed"),
                metadataMarqueeDelay = json.int("metadataMarqueeDelay", defaults.metadataMarqueeDelay, "key_hook_marquee_metadata_delay"),
                metadataMarqueeLoopDelay = json.int("metadataMarqueeLoopDelay", defaults.metadataMarqueeLoopDelay, "key_hook_marquee_metadata_loop_delay"),
                metadataMarqueeInfinite = json.boolean("metadataMarqueeInfinite", defaults.metadataMarqueeInfinite, "key_hook_marquee_metadata_infinite"),
                syllableRelative = json.boolean("syllableRelative", defaults.syllableRelative, "key_hook_syllable_relative"),
                syllableHighlight = json.boolean("syllableHighlight", defaults.syllableHighlight, "key_hook_syllable_highlight"),
                syllableLineDisplay = json.boolean("syllableLineDisplay", defaults.syllableLineDisplay, "key_hook_syllable_line_display"),
                wordMotionEnabled = json.boolean("wordMotionEnabled", defaults.wordMotionEnabled, "key_hook_word_motion_enabled"),
                wordMotionLatinByCharacter = json.boolean("wordMotionLatinByCharacter", defaults.wordMotionLatinByCharacter, "key_hook_word_motion_latin_by_character"),
                wordMotionCjkLift = json.float("wordMotionCjkLift", defaults.wordMotionCjkLift, "key_hook_word_motion_cjk_lift"),
                wordMotionCjkWave = json.float("wordMotionCjkWave", defaults.wordMotionCjkWave, "key_hook_word_motion_cjk_wave"),
                wordMotionLatinLift = json.float("wordMotionLatinLift", defaults.wordMotionLatinLift, "key_hook_word_motion_latin_lift"),
                wordMotionLatinWave = json.float("wordMotionLatinWave", defaults.wordMotionLatinWave, "key_hook_word_motion_latin_wave"),
                disableTranslation = disableTranslation,
                translationOnly = json.boolean("translationOnly", defaults.translationOnly, "key_hook_translation_only"),
                swapTranslation = json.boolean("swapTranslation", defaults.swapTranslation, "key_hook_swap_translation"),
                nextLyricLine = json.boolean("nextLyricLine", defaults.nextLyricLine, "key_hook_next_lyric_line"),
                autoSwitchTranslation = json.boolean("autoSwitchTranslation", defaults.autoSwitchTranslation, "key_hook_auto_switch_translation"),
                animation = enumOrDefault(json.string("animation"), defaults.animation),
                animEnabled = json.boolean("animEnabled", defaults.animEnabled, "key_hook_anim_enable"),
                animId = json.string("animId", "key_hook_anim_id").ifBlank { defaults.animId },
                showProgress = json.boolean("showProgress", defaults.showProgress),
                secondaryTextSizeSp = json.float("secondaryTextSizeSp", defaults.secondaryTextSizeSp),
                secondaryTextColor = json.int("secondaryTextColor", defaults.secondaryTextColor),
                displayTranslation = displayTranslation,
                bold = json.boolean("bold", defaults.bold),
            ).normalized()
        }.getOrDefault(LyricIslandConfig())
    }

    private fun encodeMediaCard(config: MediaCardConfig): JsonObject = buildJsonObject {
        val notification = config.notification
        val island = config.islandExpanded
        val aod = config.alwaysOnDisplay
        put("removeIslandWhitelist", config.removeIslandWhitelist)
        put("notification", buildJsonObject {
            put("cardSwitcherEnabled", notification.cardSwitcherEnabled)
            put("cardSwitcherMode", notification.cardSwitcherMode)
            put("cardSwitcherMaxCount", notification.cardSwitcherMaxCount)
            put("layoutStyle", notification.layoutStyle)
            put("ambientFlowMode", notification.ambientFlowMode)
            put("cardTheme", notification.cardTheme)
            put("coverStyle", notification.coverStyle)
            put("progressStyle", notification.progressStyle)
            put("progressHeadGlow", notification.progressHeadGlow)
            put("thumbStyle", notification.thumbStyle)
            put("hideCoverSource", notification.hideCoverSource)
            put("hideCoverShadow", notification.hideCoverShadow)
            put("disableCoverFlip", notification.disableCoverFlip)
            put("hideDeviceSwitch", notification.hideDeviceSwitch)
            put("hideCustomActions", notification.hideCustomActions)
            put("hideTime", notification.hideTime)
            put("actionAlignLeft", notification.actionAlignLeft)
            put("actionOrder", notification.actionOrder)
            put("backgroundStyle", notification.backgroundStyle)
            put("backgroundBlur", notification.backgroundBlur)
            put("backgroundColorAnimation", notification.backgroundColorAnimation)
            put("backgroundAutoInvert", notification.backgroundAutoInvert)
            put("softCoverTone", notification.softCoverTone)
        })
        put("islandExpanded", buildJsonObject {
            put("layoutStyle", island.layoutStyle)
            put("ambientFlowMode", island.ambientFlowMode)
            put("cardTheme", island.cardTheme)
            put("coverStyle", island.coverStyle)
            put("progressStyle", island.progressStyle)
            put("progressHeadGlow", island.progressHeadGlow)
            put("thumbStyle", island.thumbStyle)
            put("hideCoverSource", island.hideCoverSource)
            put("disableCoverFlip", island.disableCoverFlip)
            put("hideDeviceSwitch", island.hideDeviceSwitch)
            put("hideCustomActions", island.hideCustomActions)
            put("hideTime", island.hideTime)
            put("actionAlignLeft", island.actionAlignLeft)
            put("actionOrder", island.actionOrder)
            put("backgroundStyle", island.backgroundStyle)
            put("backgroundBlur", island.backgroundBlur)
            put("backgroundColorAnimation", island.backgroundColorAnimation)
            put("backgroundAutoInvert", island.backgroundAutoInvert)
            put("softCoverTone", island.softCoverTone)
        })
        put("alwaysOnDisplay", buildJsonObject {
            put("disableMediaCardCollapsing", aod.disableMediaCardCollapsing)
        })
    }

    private fun decodeMediaCard(root: JsonObject, defaults: MediaCardConfig): MediaCardConfig {
        val media = root["mediaCard"] as? JsonObject
        val mediaRoot = media ?: root
        val notification = (media?.get("notification") as? JsonObject) ?: media ?: root
        val island = (media?.get("islandExpanded") as? JsonObject) ?: media ?: root
        val aod = (media?.get("alwaysOnDisplay") as? JsonObject) ?: media ?: root
        fun JsonObject.intAt(name: String, default: Int, vararg aliases: String): Int =
            int(name, default, *aliases)
        fun JsonObject.booleanAt(name: String, default: Boolean, vararg aliases: String): Boolean =
            boolean(name, default, *aliases)
        val n = defaults.notification
        val i = defaults.islandExpanded
        return MediaCardConfig(
            removeIslandWhitelist = mediaRoot.boolean(
                "removeIslandWhitelist",
                defaults.removeIslandWhitelist,
                "key_hook_remove_island_whitelist",
            ),
            notification = NotificationMediaCardConfig(
                cardSwitcherEnabled = notification.booleanAt("cardSwitcherEnabled", n.cardSwitcherEnabled, "key_hook_notification_media_card_switcher_enabled"),
                cardSwitcherMode = notification.intAt("cardSwitcherMode", n.cardSwitcherMode, "key_hook_notification_media_card_switcher_mode"),
                cardSwitcherMaxCount = notification.intAt("cardSwitcherMaxCount", n.cardSwitcherMaxCount, "key_hook_notification_media_card_switcher_max_count"),
                layoutStyle = notification.intAt("layoutStyle", n.layoutStyle, "key_hook_notification_media_layout_style"),
                ambientFlowMode = notification.intAt("ambientFlowMode", n.ambientFlowMode, "key_hook_notification_media_ambient_flow_mode"),
                cardTheme = notification.intAt("cardTheme", n.cardTheme, "key_hook_notification_media_card_theme"),
                coverStyle = notification.intAt("coverStyle", n.coverStyle, "key_hook_notification_media_cover_style"),
                progressStyle = notification.intAt("progressStyle", n.progressStyle, "key_hook_notification_media_progress_style"),
                progressHeadGlow = notification.booleanAt(
                    "progressHeadGlow",
                    n.progressHeadGlow,
                    "key_hook_notification_media_progress_head_glow",
                ) || notification.intAt(
                    "progressStyle",
                    n.progressStyle,
                    "key_hook_notification_media_progress_style",
                ) == 2,
                thumbStyle = notification.intAt("thumbStyle", n.thumbStyle, "key_hook_notification_media_thumb_style"),
                hideCoverSource = notification.booleanAt("hideCoverSource", n.hideCoverSource, "key_hook_notification_media_hide_cover_source"),
                hideCoverShadow = notification.booleanAt("hideCoverShadow", n.hideCoverShadow, "key_hook_notification_media_hide_cover_shadow"),
                disableCoverFlip = notification.booleanAt("disableCoverFlip", n.disableCoverFlip, "key_hook_notification_media_disable_cover_flip"),
                hideDeviceSwitch = notification.booleanAt("hideDeviceSwitch", n.hideDeviceSwitch, "key_hook_notification_media_hide_device_switch"),
                hideCustomActions = notification.booleanAt("hideCustomActions", n.hideCustomActions, "key_hook_notification_media_hide_custom_actions"),
                hideTime = notification.booleanAt("hideTime", n.hideTime, "key_hook_notification_media_hide_time"),
                actionAlignLeft = notification.booleanAt("actionAlignLeft", n.actionAlignLeft, "key_hook_notification_media_action_align_left"),
                actionOrder = notification.intAt("actionOrder", n.actionOrder, "key_hook_notification_media_action_order"),
                backgroundStyle = notification.intAt("backgroundStyle", n.backgroundStyle, "key_hook_notification_media_background_style"),
                backgroundBlur = notification.intAt("backgroundBlur", n.backgroundBlur, "key_hook_notification_media_background_blur"),
                backgroundColorAnimation = notification.booleanAt("backgroundColorAnimation", n.backgroundColorAnimation, "key_hook_notification_media_background_color_animation"),
                backgroundAutoInvert = notification.booleanAt("backgroundAutoInvert", n.backgroundAutoInvert, "key_hook_notification_media_background_auto_invert"),
                softCoverTone = notification.intAt("softCoverTone", n.softCoverTone, "key_hook_notification_media_soft_cover_tone"),
            ),
            islandExpanded = IslandExpandedMediaCardConfig(
                layoutStyle = island.intAt("layoutStyle", i.layoutStyle, "key_hook_island_expanded_media_layout_style"),
                ambientFlowMode = island.intAt("ambientFlowMode", i.ambientFlowMode, "key_hook_island_expanded_media_ambient_flow_mode"),
                cardTheme = island.intAt("cardTheme", i.cardTheme, "key_hook_island_expanded_media_card_theme"),
                coverStyle = island.intAt("coverStyle", i.coverStyle, "key_hook_island_expanded_media_cover_style"),
                progressStyle = island.intAt("progressStyle", i.progressStyle, "key_hook_island_expanded_media_progress_style"),
                progressHeadGlow = island.booleanAt("progressHeadGlow", i.progressHeadGlow, "key_hook_island_expanded_media_progress_head_glow"),
                thumbStyle = island.intAt("thumbStyle", i.thumbStyle, "key_hook_island_expanded_media_thumb_style"),
                hideCoverSource = island.booleanAt("hideCoverSource", i.hideCoverSource, "key_hook_island_expanded_media_hide_cover_source"),
                disableCoverFlip = island.booleanAt("disableCoverFlip", i.disableCoverFlip, "key_hook_island_expanded_media_disable_cover_flip"),
                hideDeviceSwitch = island.booleanAt("hideDeviceSwitch", i.hideDeviceSwitch, "key_hook_island_expanded_media_hide_device_switch"),
                hideCustomActions = island.booleanAt("hideCustomActions", i.hideCustomActions, "key_hook_island_expanded_media_hide_custom_actions"),
                hideTime = island.booleanAt("hideTime", i.hideTime, "key_hook_island_expanded_media_hide_time"),
                actionAlignLeft = island.booleanAt("actionAlignLeft", i.actionAlignLeft, "key_hook_island_expanded_media_action_align_left"),
                actionOrder = island.intAt("actionOrder", i.actionOrder, "key_hook_island_expanded_media_action_order"),
                backgroundStyle = island.intAt("backgroundStyle", i.backgroundStyle, "key_hook_island_expanded_media_background_style"),
                backgroundBlur = island.intAt("backgroundBlur", i.backgroundBlur, "key_hook_island_expanded_media_background_blur"),
                backgroundColorAnimation = island.booleanAt("backgroundColorAnimation", i.backgroundColorAnimation, "key_hook_island_expanded_media_background_color_animation"),
                backgroundAutoInvert = island.booleanAt("backgroundAutoInvert", i.backgroundAutoInvert, "key_hook_island_expanded_media_background_auto_invert"),
                softCoverTone = island.intAt("softCoverTone", i.softCoverTone, "key_hook_island_expanded_media_soft_cover_tone"),
            ),
            alwaysOnDisplay = AlwaysOnDisplayMediaCardConfig(
                disableMediaCardCollapsing = aod.boolean("disableMediaCardCollapsing", defaults.alwaysOnDisplay.disableMediaCardCollapsing, "key_hook_aod_disable_media_card_collapsing"),
            ),
        ).normalized()
    }

    private fun JsonObject.value(key: String, vararg aliases: String): JsonPrimitive? {
        (this[key] as? JsonPrimitive)?.let { return it }
        for (alias in aliases) (this[alias] as? JsonPrimitive)?.let { return it }
        return null
    }

    private fun JsonObject.string(key: String, vararg aliases: String): String = value(key, *aliases)?.contentOrNull.orEmpty()
    private fun JsonObject.boolean(key: String, default: Boolean, vararg aliases: String): Boolean =
        value(key, *aliases)?.booleanOrNull ?: default
    private fun JsonObject.float(key: String, default: Float, vararg aliases: String): Float =
        value(key, *aliases)?.floatOrNull ?: default
    private fun JsonObject.int(key: String, default: Int, vararg aliases: String): Int =
        value(key, *aliases)?.intOrNull ?: default

    private fun JsonObject.objectIntMap(key: String): Map<String, Int> =
        (this[key] as? JsonObject).orEmpty().entries.asSequence()
            .mapNotNull { (packageName, value) ->
                (value as? JsonPrimitive)?.intOrNull?.let { packageName to it }
            }
            .take(64)
            .toMap()
    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, default: T): T =
        runCatching { enumValueOf<T>(raw.uppercase()) }.getOrDefault(default)

    private fun sourceModeOrDefault(raw: String, default: LyricSourceMode): LyricSourceMode =
        LyricSourceMode.fromWire(raw) ?: default

    private fun contentModeOrDefault(value: JsonPrimitive?, default: IslandContentMode): IslandContentMode {
        value?.intOrNull?.let { return IslandContentMode.fromWire(it) ?: default }
        return value?.contentOrNull?.let {
            IslandContentMode.fromWire(it) ?: default
        } ?: default
    }

    private fun placeholderOrDefault(value: JsonPrimitive?, default: LyricPlaceholder): LyricPlaceholder {
        value?.intOrNull?.let {
            return when (it) {
                0 -> LyricPlaceholder.NONE
                1 -> LyricPlaceholder.NAME_ARTIST
                2 -> LyricPlaceholder.NAME
                3 -> LyricPlaceholder.COUNTDOWN
                else -> default
            }
        }
        value?.contentOrNull?.let { raw ->
            raw.toIntOrNull()?.let { numeric ->
                return LyricPlaceholder.entries.firstOrNull { it.wireValue == numeric } ?: default
            }
            return runCatching { enumValueOf<LyricPlaceholder>(raw.uppercase()) }
                .getOrDefault(default)
        }
        return default
    }

    private fun legacyDefaults(): LyricIslandConfig = LyricIslandConfig(
        sourceMode = LyricSourceMode.SUPER_LYRIC,
        slot = LyricSlot.LEFT,
        textSizeSp = 14f,
        highlightColor = 0xFFFF5722.toInt(),
        fadingEdgeLengthDp = 10,
        placeholder = LyricPlaceholder.NAME_ARTIST,
        secondaryTextSizeSp = 11f,
    )
}
