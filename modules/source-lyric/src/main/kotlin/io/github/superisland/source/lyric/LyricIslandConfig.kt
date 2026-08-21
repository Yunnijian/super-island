package io.github.superisland.source.lyric

data class LyricIslandConfig(
    val enabled: Boolean = false,
    val sourceMode: LyricSourceMode = LyricSourceMode.SUPER_LYRIC,
    val animation: LyricAnimation = LyricAnimation.WORD_SYNC,
    val showProgress: Boolean = true,
) {
    fun normalized(): LyricIslandConfig = copy()
}

enum class LyricSourceMode { SUPER_LYRIC, MEDIA_FALLBACK }
enum class LyricAnimation { NONE, WORD_SYNC, SMOOTH }
