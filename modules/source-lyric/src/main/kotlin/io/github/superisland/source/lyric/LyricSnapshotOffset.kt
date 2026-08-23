package io.github.superisland.source.lyric

/** Applies HyperLyric's Lyricon provider delay without changing the source payload. */
object LyricSnapshotOffset {
    fun apply(snapshot: LyricSnapshot, offsetMs: Int): LyricSnapshot {
        if (offsetMs == 0) return snapshot
        fun shift(line: LyricLine?): LyricLine? = line?.copy(
            startMs = (line.startMs + offsetMs).coerceAtLeast(0L),
            endMs = (line.endMs + offsetMs).coerceAtLeast(0L),
            words = line.words.map { word ->
                word.copy(
                    startMs = (word.startMs + offsetMs).coerceAtLeast(0L),
                    endMs = (word.endMs + offsetMs).coerceAtLeast(0L),
                )
            },
        )
        return snapshot.copy(
            line = shift(snapshot.line),
            secondary = shift(snapshot.secondary),
            translation = shift(snapshot.translation),
        )
    }
}
