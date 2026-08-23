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
    /** Optional source metadata retained for the rich HyperLyric renderer. */
    val isTitleLine: Boolean = false,
)

/** Playback state pushed by SuperLyric. Position is the last player-reported position. */
data class LyricPlayback(
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
) {
    fun normalized(): LyricPlayback = copy(
        positionMs = positionMs.coerceAtLeast(0L),
        speed = speed.takeIf { it.isFinite() && it >= 0f } ?: 1f,
        durationMs = durationMs.coerceAtLeast(0L),
    )
}

/** Source-independent rich lyric snapshot, matching HyperLyric's RootLyricSink contract. */
data class LyricSnapshot(
    val publisher: String,
    val line: LyricLine? = null,
    val secondary: LyricLine? = null,
    val translation: LyricLine? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** Cover-derived colors supplied by the MediaSession resolver for adaptive text styles. */
    val artworkColors: List<Int> = emptyList(),
    val playback: LyricPlayback = LyricPlayback(),
    val stopped: Boolean = false,
) {
    val hasContent: Boolean
        get() = line != null || secondary != null || translation != null ||
            !title.isNullOrBlank() || !artist.isNullOrBlank() || !album.isNullOrBlank()

    fun normalized(): LyricSnapshot = copy(
        publisher = publisher.trim().take(128),
        line = line?.normalized(),
        secondary = secondary?.normalized(),
        translation = translation?.normalized(),
        title = title?.trim()?.takeIf(String::isNotBlank)?.take(200),
        artist = artist?.trim()?.takeIf(String::isNotBlank)?.take(200),
        album = album?.trim()?.takeIf(String::isNotBlank)?.take(200),
        artworkColors = artworkColors.take(8),
        playback = playback.normalized(),
    )
}

private fun LyricLine.normalized(): LyricLine {
    val safeWords = words
        .filter { it.text.isNotEmpty() }
        .map { word ->
            val start = word.startMs.coerceAtLeast(0L)
            val end = maxOf(start, word.endMs)
            word.copy(
                text = word.text.take(120),
                startMs = start,
                endMs = end,
            )
        }
    val joined = safeWords.joinToString("") { it.text }
    return copy(
        text = (if (joined.isNotBlank()) joined else text).trim().take(400),
        startMs = startMs.coerceAtLeast(0L),
        endMs = maxOf(startMs.coerceAtLeast(0L), endMs),
        words = safeWords.take(256),
    )
}
