package io.github.superisland.source.lyric

import android.graphics.Color
import android.graphics.Typeface

/** Lyric source selection. */
enum class LyricSourceMode { SUPER_LYRIC, MEDIA_FALLBACK }

/** Word/line animation style. */
enum class LyricAnimation { NONE, WORD_SYNC, SMOOTH, MARQUEE }

/** Where the lyric view sits inside the island. */
enum class LyricSlot { LEFT, RIGHT }

/** Placeholder text when no song is playing. */
enum class LyricPlaceholder(val display: String) {
    NAME("歌名"),
    NAME_ARTIST("歌名 - 歌手"),
    NONE("不显示"),
}

/** Full lyric-island configuration, mirroring HyperLyric's RichLyricLineConfig surface. */
data class LyricIslandConfig(
    val enabled: Boolean = false,
    val sourceMode: LyricSourceMode = LyricSourceMode.SUPER_LYRIC,
    val slot: LyricSlot = LyricSlot.LEFT,
    val animation: LyricAnimation = LyricAnimation.WORD_SYNC,
    // Text
    val textSizeSp: Float = 14f,
    val textColor: Int = Color.WHITE,
    val highlightColor: Int = Color.parseColor("#FF5722"),
    // Secondary (translation / next-line preview)
    val displayTranslation: Boolean = true,
    val secondaryTextSizeSp: Float = 11f,
    val secondaryTextColor: Int = Color.parseColor("#B3FFFFFF"),
    // Behavior
    val showProgress: Boolean = true,
    val gradientProgressStyle: Boolean = true,
    val fadingEdgeLengthDp: Int = 10,
    val placeholder: LyricPlaceholder = LyricPlaceholder.NAME_ARTIST,
    val bold: Boolean = false,
) {
    fun normalized(): LyricIslandConfig = copy()
}
