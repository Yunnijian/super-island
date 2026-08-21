package io.github.superisland.source.lyric

object TimingNavigator {
    fun findCurrentLine(lines: List<LyricLine>, positionMs: Long): LyricLine? {
        var low = 0
        var high = lines.size - 1
        var result: LyricLine? = null
        while (low <= high) {
            val mid = (low + high) ushr 1
            val line = lines[mid]
            when {
                positionMs < line.startMs -> high = mid - 1
                positionMs > line.endMs -> low = mid + 1
                else -> return line
            }
            if (line.startMs <= positionMs) result = line
        }
        return result
    }
}
