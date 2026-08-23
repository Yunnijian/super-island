package io.github.superisland.source.lyric

import android.graphics.Color

/** The basic HyperLyric providers and their persisted source ids. */
enum class LyricSourceMode(val wireValue: String) {
    LYRICON("lyricon"),
    SUPER_LYRIC("superlyric"),
    LYRIC_INFO("lyricinfo"),
    /** Compatibility value for configurations from an older Super Island build. */
    MEDIA_FALLBACK("media_fallback"),

    ;

    companion object {
        fun fromWire(value: String?): LyricSourceMode? {
            val normalized = value?.trim()?.lowercase()?.replace('-', '_') ?: return null
            return entries.firstOrNull { it.wireValue == normalized }
                ?: when (normalized) {
                    "lyricon" -> LYRICON
                    "super_lyric", "super-lyric" -> SUPER_LYRIC
                    "lyric_info", "lyric-info" -> LYRIC_INFO
                    "media", "mediafallback", "media-fallback" -> MEDIA_FALLBACK
                    else -> runCatching { value.trim().uppercase().let(::valueOf) }.getOrNull()
                }
        }
    }

    /** Maps retired local compatibility values to the closest public HyperLyric source. */
    fun publicPickerMode(): LyricSourceMode =
        if (this == MEDIA_FALLBACK) LYRIC_INFO else this
}

/** Legacy compatibility enum; the upstream picker uses animEnabled/animId. */
enum class LyricAnimation { NONE, WORD_SYNC, SMOOTH, MARQUEE }

enum class LyricSlot { LEFT, RIGHT }

enum class LyricPlaceholder(val display: String, val wireValue: Int) {
    NONE("无", 0),
    NAME_ARTIST("歌名 - 歌手", 1),
    NAME("歌名", 2),
    COUNTDOWN("倒计时圆点", 3),
}

enum class IslandContentMode(val wireValue: Int) {
    NONE(0),
    LYRIC(7),
    MUSIC_INFO(8),

    ;

    companion object {
        fun fromWire(value: String?): IslandContentMode? {
            val normalized = value?.trim()?.lowercase()?.replace('-', '_') ?: return null
            return when (normalized) {
                "none", "0" -> NONE
                "lyric", "lyrics", "7" -> LYRIC
                "music_info", "custom_music_info", "musicinfo", "8" -> MUSIC_INFO
                else -> runCatching { value.trim().uppercase().let(::valueOf) }.getOrNull()
            }
        }

        fun fromWire(value: Int?): IslandContentMode? = when (value) {
            0 -> NONE
            7 -> LYRIC
            8 -> MUSIC_INFO
            else -> null
        }
    }
}

/** Shared HyperLyric music metadata field and separator contract. */
object LyricMusicInfoLayout {
    const val FIELD_TITLE = "title"
    const val FIELD_ARTIST = "artist"
    const val FIELD_ALBUM = "album"
    const val FIELD_DURATION = "duration"
    const val FIELD_ELAPSED = "elapsed"
    const val FIELD_REMAINING = "remaining"
    const val FIELD_PROGRESS_PERCENT = "progress_percent"

    const val SEPARATOR_PLUS = "plus"
    const val SEPARATOR_SPACE = "space"
    const val SEPARATOR_COMMA = "comma"
    const val SEPARATOR_IDEOGRAPHIC_COMMA = "ideographic_comma"
    const val SEPARATOR_SLASH = "slash"
    const val SEPARATOR_HYPHEN = "hyphen"
    const val SEPARATOR_NONE = "none"

    /** Stable display order copied from HyperLyric's ContentLayoutField enum. */
    val fieldOrder: List<String> = listOf(
        FIELD_TITLE,
        FIELD_ARTIST,
        FIELD_ALBUM,
        FIELD_DURATION,
        FIELD_ELAPSED,
        FIELD_REMAINING,
        FIELD_PROGRESS_PERCENT,
    )

    val fieldLabels: Map<String, String> = linkedMapOf(
        FIELD_TITLE to "标题",
        FIELD_ARTIST to "艺术家",
        FIELD_ALBUM to "专辑",
        FIELD_DURATION to "总时长",
        FIELD_ELAPSED to "已播放时间",
        FIELD_REMAINING to "剩余时间",
        FIELD_PROGRESS_PERCENT to "播放进度",
    )

    val supportedFields: Set<String> = fieldOrder.toSet()

    fun parseFields(raw: String?, defaultField: String): List<String> {
        // HyperLyric distinguishes an absent preference from an explicitly empty list. The
        // former gets the source default; the latter is a valid "no field" row (used for the
        // optional second line).
        if (raw == null) return listOf(defaultField)
        return raw
            .split(',')
            .map(String::trim)
            .filter { it in supportedFields }
            .distinct()
    }

    fun encodeFields(fields: List<String>, defaultField: String): String =
        fields.filter { it in supportedFields }.distinct()
            .joinToString(",")

    fun normalizeFields(raw: String?, defaultField: String): String {
        return encodeFields(parseFields(raw, defaultField), defaultField)
    }

    fun normalizeSeparator(raw: String?): String {
        return raw.orEmpty().trim().takeIf {
            it in setOf(
                SEPARATOR_PLUS,
                SEPARATOR_SPACE,
                SEPARATOR_COMMA,
                SEPARATOR_IDEOGRAPHIC_COMMA,
                SEPARATOR_SLASH,
                SEPARATOR_HYPHEN,
                SEPARATOR_NONE,
            )
        } ?: SEPARATOR_HYPHEN
    }
}

/** Width bounds copied from HyperLyric's SuperIslandWidthPolicy. */
object LyricIslandWidthPolicy {
    const val SIDE_COMPONENT_WIDTH_DP = 22
    const val MIN_ISLAND_WIDTH_DP = 22
    const val MAX_SIDE_WIDTH_DP = 170
    const val SYSTEM_LIMITED_MAX_SIDE_WIDTH_DP = 114

    fun isAlbumCoverVisible(style: Int): Boolean = style != LyricAlbumCoverStyle.HIDDEN

    fun isMusicWaveVisible(style: Int): Boolean = style != 3

    fun minIslandWidth(showAlbum: Boolean, showRhythm: Boolean): Int =
        (componentWidth(showAlbum) - componentWidth(showRhythm))
            .coerceAtLeast(MIN_ISLAND_WIDTH_DP)

    fun maxIslandWidth(showRhythm: Boolean, disableWidthLimit: Boolean): Int =
        (if (disableWidthLimit) MAX_SIDE_WIDTH_DP else SYSTEM_LIMITED_MAX_SIDE_WIDTH_DP) -
            componentWidth(showRhythm)

    fun normalizeIslandWidth(
        width: Int,
        showAlbum: Boolean,
        showRhythm: Boolean,
        disableWidthLimit: Boolean,
    ): Int {
        val min = minIslandWidth(showAlbum, showRhythm)
        val max = maxIslandWidth(showRhythm, disableWidthLimit).coerceAtLeast(min)
        return width.coerceIn(min, max)
    }

    private fun componentWidth(visible: Boolean): Int =
        if (visible) SIDE_COMPONENT_WIDTH_DP else 0
}

/**
 * HyperLyric's persisted values are not in the same order as the user-facing picker: the app
 * icon is wire value 2 while the rotating circle is wire value 3. Keep both the constants and
 * picker order in one contract so a UI index is never written as the wrong style.
 */
object LyricAlbumCoverStyle {
    const val DEFAULT = 0
    const val CIRCLE = 1
    const val APP_ICON = 2
    const val ROTATING_CIRCLE = 3
    const val HIDDEN = 4

    /** Display order copied from HyperLyric's audioCoverStyleOptions. */
    val pickerValues: List<Int> = listOf(DEFAULT, CIRCLE, ROTATING_CIRCLE, APP_ICON, HIDDEN)
    val pickerLabels: List<String> = listOf("默认", "圆形封面", "旋转圆形封面", "应用图标", "隐藏封面")

    fun pickerIndex(wireValue: Int): Int = pickerValues.indexOf(wireValue).takeIf { it >= 0 } ?: 0
}

/**
 * Basic HyperLyric configuration shared by the app, SystemUI host and rich renderer.
 * Field names/defaults follow HyperLyric's RootConstants; special media-card/glow settings are
 * deliberately absent from the user-facing screens.
 */
data class LyricIslandConfig(
    val enabled: Boolean = false,
    val sourceMode: LyricSourceMode = LyricSourceMode.LYRICON,
    val lyriconProviderDelayMs: Int = 0,
    /** HyperLyric stores a separate delay under key_hook_lyricon_provider_delay_<package>. */
    val lyriconProviderDelays: Map<String, Int> = emptyMap(),
    val lyricMode: Int = 0,

    // Content layout
    val contentLeft: IslandContentMode = IslandContentMode.MUSIC_INFO,
    val contentRight: IslandContentMode = IslandContentMode.LYRIC,
    val musicInfoFirstLine: String = "title",
    val musicInfoSecondLine: String = "artist",
    val musicInfoSeparator: String = "hyphen",
    val centerMusicInfo: Boolean = false,
    val centerLyric: Boolean = false,
    val rightLyric: Boolean = false,
    val widthMode: Int = 0,
    val dynamicWidthBasis: Int = 0,
    val rightContentMaxWidth: Int = 72,
    val dynamicMinWidth: Int = 22,
    val dynamicMaxWidth: Int = 72,
    val disableWidthLimit: Boolean = false,
    val leftPaddingLeft: Int = 2,
    val leftPaddingRight: Int = 0,
    val rightPaddingLeft: Int = 0,
    val rightPaddingRight: Int = 0,
    val albumCoverStyle: Int = LyricAlbumCoverStyle.DEFAULT,
    val musicWaveStyle: Int = 0,
    // Keep the compact payload's historical left-slot default. HyperLyric's newer content
    // layout is still represented by contentLeft/contentRight/rightLyric above.
    val slot: LyricSlot = LyricSlot.LEFT,

    // Text/style
    val textSizeSp: Float = 12f,
    val textSizeRatio: Float = 0.7f,
    val textColorStyle: Int = 0,
    val textColor: Int = Color.WHITE,
    val highlightColor: Int = Color.WHITE,
    val customFontPath: String = "",
    val fontWeight: Int = 600,
    val fontItalic: Boolean = false,
    val narrowLatinFont: Boolean = false,
    val fadingEdgeLengthDp: Int = 15,
    val gradientProgressStyle: Boolean = true,
    val placeholder: LyricPlaceholder = LyricPlaceholder.COUNTDOWN,

    // Lyric scrolling
    val marqueeMode: Boolean = false,
    val marqueeSpeed: Int = 30,
    val marqueeDelay: Int = 1500,
    val marqueeLoopDelay: Int = 1000,
    val marqueeInfinite: Boolean = false,
    val marqueeStopEnd: Boolean = true,
    val metadataMarqueeMode: Boolean = true,
    val metadataMarqueeSpeed: Int = 25,
    val metadataMarqueeDelay: Int = 1000,
    val metadataMarqueeLoopDelay: Int = 0,
    val metadataMarqueeInfinite: Boolean = true,

    // Verbatim lyrics
    val syllableRelative: Boolean = true,
    val syllableHighlight: Boolean = false,
    val syllableLineDisplay: Boolean = false,
    val wordMotionEnabled: Boolean = false,
    val wordMotionLatinByCharacter: Boolean = false,
    val wordMotionCjkLift: Float = 0.05f,
    val wordMotionCjkWave: Float = 2.8f,
    val wordMotionLatinLift: Float = 0.06f,
    val wordMotionLatinWave: Float = 3.6f,

    // Double-line content
    val disableTranslation: Boolean = false,
    val translationOnly: Boolean = false,
    val swapTranslation: Boolean = false,
    val nextLyricLine: Boolean = false,
    val autoSwitchTranslation: Boolean = false,

    // Lyric transition animation
    val animation: LyricAnimation = LyricAnimation.WORD_SYNC,
    val animEnabled: Boolean = false,
    val animId: String = "default",

    // Compatibility fields for old payload callers. They are not separate UI features.
    val showProgress: Boolean = true,
    val secondaryTextSizeSp: Float = 8.4f,
    val secondaryTextColor: Int = 0xB3FFFFFF.toInt(),
    val displayTranslation: Boolean = true,
    val bold: Boolean = false,
) {
    fun withEnabled(value: Boolean): LyricIslandConfig = copy(enabled = value)

    fun lyricSlot(): LyricSlot = when {
        contentRight == IslandContentMode.LYRIC -> LyricSlot.RIGHT
        contentLeft == IslandContentMode.LYRIC -> LyricSlot.LEFT
        else -> slot
    }

    fun hasLyricContent(): Boolean =
        lyricMode == 1 ||
            contentLeft == IslandContentMode.LYRIC || contentRight == IslandContentMode.LYRIC

    fun normalized(): LyricIslandConfig {
        val normalizedAlbumCoverStyle = albumCoverStyle.coerceIn(0, 4)
        val normalizedMusicWaveStyle = musicWaveStyle.coerceIn(0, 3)
        val showAlbum = LyricIslandWidthPolicy.isAlbumCoverVisible(normalizedAlbumCoverStyle)
        val showRhythm = LyricIslandWidthPolicy.isMusicWaveVisible(normalizedMusicWaveStyle)
        val minWidth = LyricIslandWidthPolicy.minIslandWidth(showAlbum, showRhythm)
        val maxWidth = LyricIslandWidthPolicy.maxIslandWidth(showRhythm, disableWidthLimit)
            .coerceAtLeast(minWidth)
        val normalizedDynamicMin = dynamicMinWidth.coerceIn(minWidth, maxWidth)
        val normalizedDynamicMax = dynamicMaxWidth.coerceIn(normalizedDynamicMin, maxWidth)
        return copy(
            lyricMode = lyricMode.coerceIn(0, 1),
            lyriconProviderDelayMs = lyriconProviderDelayMs.coerceIn(-5000, 5000),
            lyriconProviderDelays = lyriconProviderDelays.asSequence()
                .filter { it.key.isNotBlank() }
                .take(64)
                .associate { it.key.trim().take(128) to it.value.coerceIn(-5000, 5000) },
            contentLeft = contentLeft,
            contentRight = contentRight,
            musicInfoFirstLine = LyricMusicInfoLayout.normalizeFields(musicInfoFirstLine, LyricMusicInfoLayout.FIELD_TITLE)
                .take(64),
            musicInfoSecondLine = LyricMusicInfoLayout.normalizeFields(musicInfoSecondLine, LyricMusicInfoLayout.FIELD_ARTIST)
                .take(64),
            musicInfoSeparator = LyricMusicInfoLayout.normalizeSeparator(musicInfoSeparator),
            widthMode = widthMode.coerceIn(0, 1),
            dynamicWidthBasis = dynamicWidthBasis.coerceIn(0, 1),
            rightContentMaxWidth = LyricIslandWidthPolicy.normalizeIslandWidth(
                rightContentMaxWidth,
                showAlbum,
                showRhythm,
                disableWidthLimit,
            ),
            dynamicMinWidth = normalizedDynamicMin,
            dynamicMaxWidth = normalizedDynamicMax,
            albumCoverStyle = normalizedAlbumCoverStyle,
            musicWaveStyle = normalizedMusicWaveStyle,
            textSizeSp = textSizeSp.takeIf { it.isFinite() }?.coerceIn(8f, 16f) ?: 12f,
            textSizeRatio = textSizeRatio.takeIf { it.isFinite() }?.coerceIn(0.1f, 1f) ?: 0.7f,
            textColorStyle = textColorStyle.coerceIn(0, 3),
            secondaryTextSizeSp = secondaryTextSizeSp.takeIf { it.isFinite() }?.coerceIn(6f, 16f) ?: 8.4f,
            fontWeight = fontWeight.coerceIn(100, 900),
            fadingEdgeLengthDp = fadingEdgeLengthDp.coerceIn(0, 100),
            marqueeSpeed = marqueeSpeed.coerceIn(5, 100),
            marqueeDelay = marqueeDelay.coerceIn(0, 10_000),
            marqueeLoopDelay = marqueeLoopDelay.coerceIn(0, 10_000),
            metadataMarqueeSpeed = metadataMarqueeSpeed.coerceIn(5, 100),
            metadataMarqueeDelay = metadataMarqueeDelay.coerceIn(0, 10_000),
            metadataMarqueeLoopDelay = metadataMarqueeLoopDelay.coerceIn(0, 10_000),
            // `syllableLineDisplay` is an old local compatibility value. It has no public
            // control, but configurations that already contain it must keep their behavior.
            syllableHighlight = syllableHighlight && syllableRelative,
            // The former `displayTranslation` field is not user-facing. Keep it as a wire
            // mirror so an old false value cannot override the public translation switch.
            displayTranslation = !disableTranslation,
            // Song-level automatic translation switching is deliberately outside the basic
            // Super Island lyric scope. Retire a persisted legacy value without rejecting it.
            autoSwitchTranslation = false,
            wordMotionCjkLift = wordMotionCjkLift.takeIf { it.isFinite() }?.coerceIn(0f, 0.2f) ?: 0.05f,
            wordMotionCjkWave = wordMotionCjkWave.takeIf { it.isFinite() }?.coerceIn(0f, 8f) ?: 2.8f,
            wordMotionLatinLift = wordMotionLatinLift.takeIf { it.isFinite() }?.coerceIn(0f, 0.2f) ?: 0.06f,
            wordMotionLatinWave = wordMotionLatinWave.takeIf { it.isFinite() }?.coerceIn(0f, 8f) ?: 3.6f,
            customFontPath = customFontPath.take(512),
            animId = animId.takeIf { it.isNotBlank() }?.take(64) ?: "default",
            // HyperLyric accepts -50..100 in the editor; the SystemUI geometry layer clamps
            // negative values to zero when converting to pixels.
            leftPaddingLeft = leftPaddingLeft.coerceIn(-50, 100),
            leftPaddingRight = leftPaddingRight.coerceIn(-50, 100),
            rightPaddingLeft = rightPaddingLeft.coerceIn(-50, 100),
            rightPaddingRight = rightPaddingRight.coerceIn(-50, 100),
        )
    }
}
