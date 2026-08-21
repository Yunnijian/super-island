package io.github.superisland.source.lyric

object LrcParser {
    private const val MAX_LINES = 5000
    fun parseLine(raw: String): LyricLine? {
        if (raw.isBlank()) return null
        // SuperLyric already returns parsed line; keep raw as fallback and truncate.
        val text = raw.take(200).trim()
        if (text.isEmpty()) return null
        return LyricLine(text = text, startMs = 0, endMs = 0, words = emptyList())
    }
    fun parseLrc(content: String): List<LyricLine> {
        return content.lineSequence().take(MAX_LINES).mapNotNull { parseLine(it) }.toList()
    }
}
