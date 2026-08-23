package io.github.superisland.source.lyric

/** Bounded LRC/ELRC parser used by the public MediaSession fallback. */
object LrcParser {
    private const val MAX_LINES = 5_000
    private const val MAX_TEXT_LENGTH = 400
    private val lineTime = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]", RegexOption.IGNORE_CASE)
    private val wordTime = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>([^<]*)")

    fun parseLine(raw: String): LyricLine? = parseLrc(raw).firstOrNull()

    fun parseLrc(content: String): List<LyricLine> {
        if (content.isBlank()) return emptyList()
        val parsed = buildList {
            content.lineSequence().take(MAX_LINES).forEach { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return@forEach
                val matches = lineTime.findAll(trimmed).toList()
                if (matches.isEmpty()) return@forEach
                val textPart = trimmed.replace(lineTime, "").trim()
                if (textPart.isBlank()) return@forEach
                val words = parseWords(textPart)
                matches.forEach { match ->
                    add(
                        LyricLine(
                            text = if (words.isEmpty()) textPart.take(MAX_TEXT_LENGTH)
                            else words.joinToString("") { it.text },
                            startMs = timestamp(match),
                            endMs = 0L,
                            words = words,
                        ),
                    )
                }
            }
        }.sortedBy { it.startMs }
        return parsed.mapIndexed { index, line ->
            val nextStart = parsed.getOrNull(index + 1)?.startMs
            val end = nextStart?.takeIf { it > line.startMs } ?: (line.startMs + 5_000L)
            val words = if (line.words.isEmpty()) emptyList() else line.words.mapIndexed { wordIndex, word ->
                val nextWord = line.words.getOrNull(wordIndex + 1)?.startMs
                word.copy(endMs = nextWord?.takeIf { it > word.startMs } ?: end)
            }
            line.copy(endMs = end, words = words)
        }
    }

    private fun parseWords(text: String): List<LyricWord> {
        val matches = wordTime.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.map { match ->
            LyricWord(
                text = match.groupValues[4].trim().take(120),
                startMs = timestamp(match),
                endMs = 0L,
            )
        }.filter { it.text.isNotEmpty() }
    }

    private fun timestamp(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: return 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: return 0L
        val fraction = match.groupValues[3]
        val millis = when {
            fraction.isEmpty() -> 0L
            fraction.length == 1 -> fraction.toLong() * 100L
            fraction.length == 2 -> fraction.toLong() * 10L
            else -> fraction.take(3).toLong()
        }
        return (minutes * 60_000L + seconds * 1_000L + millis).coerceAtLeast(0L)
    }
}
