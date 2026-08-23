package io.github.superisland.source.lyric

import kotlin.math.roundToInt

/** Pure timing helpers shared by the payload host, renderer adapter and unit tests. */
object LyricProgress {
    fun lineProgress(line: LyricLine?, positionMs: Long): Int? {
        if (line == null) return null
        val start = line.startMs
        val end = line.endMs
        if (end <= start) return null
        return (((positionMs - start).toDouble() / (end - start).toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun wordProgress(line: LyricLine?, positionMs: Long): Float? {
        val words = line?.words.orEmpty()
        if (words.isEmpty()) return null
        val first = words.firstOrNull { it.endMs > it.startMs } ?: return null
        val last = words.lastOrNull { it.endMs > it.startMs } ?: return null
        val start = first.startMs
        val end = last.endMs
        if (end <= start) return null
        return ((positionMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
    }

    fun activeWordIndex(line: LyricLine?, positionMs: Long): Int? =
        line?.words?.indexOfFirst { positionMs >= it.startMs && positionMs < it.endMs }
            ?.takeIf { it >= 0 }

    fun boundedText(value: String?, maxCodePoints: Int = 64): String {
        if (value.isNullOrBlank() || maxCodePoints <= 0) return ""
        val trimmed = value.trim()
        val end = trimmed.offsetByCodePoints(0, minOf(maxCodePoints, trimmed.codePointCount(0, trimmed.length)))
        return trimmed.substring(0, end)
    }
}
