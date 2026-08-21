package io.github.superisland.source.lyric

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

data class LyricLine(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<LyricWord> = emptyList(),
)
