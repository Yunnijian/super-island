package io.github.superisland.source.lyric

import io.github.superisland.source.lyric.hyperlyric.common.lyric.LyricInfoParser
import io.github.superisland.source.lyric.hyperlyric.model.RichLyricLine

/**
 * Parses the lyric payloads exposed by MediaSession metadata.
 *
 * LyricInfo JSON is parsed by the copied HyperLyric parser, keeping its translation pairing,
 * ELRC word timing, and end-time completion rules unchanged. This adapter only converts the
 * result into the source module's small, cross-process model; ordinary LRC uses [LrcParser].
 */
object LyricDocumentParser {
    private const val MAX_LINES = 5_000
    private const val MAX_TEXT_LENGTH = 400
    private const val MAX_METADATA_LENGTH = 256_000

    data class Entry(
        val line: LyricLine,
        val translation: LyricLine? = null,
    )

    data class Document(
        val entries: List<Entry>,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val songId: String? = null,
    ) {
        val lines: List<LyricLine>
            get() = entries.map { it.line }
    }

    /** Returns null for blank, malformed, or unsupported lyric text. */
    fun parse(raw: String?): Document? {
        val content = raw?.take(MAX_METADATA_LENGTH)?.trim().orEmpty()
        if (content.isBlank()) return null

        if (content.firstOrNull() == '{') {
            parseLyricInfo(content)?.let { return it }
        }
        return parseLrc(content)
    }

    private fun parseLyricInfo(content: String): Document? = runCatching {
        val payload = LyricInfoParser.parsePayload(content) ?: return null
        val entries = payload.song.lyrics.orEmpty()
            .take(MAX_LINES)
            .mapNotNull(::toEntry)
            .sortedWith(compareBy<Entry> { it.line.startMs }.thenBy { it.line.endMs })
        if (entries.isEmpty()) return null
        Document(
            entries = entries,
            title = payload.title,
            artist = payload.artist,
            album = payload.album,
            songId = payload.songId,
        )
    }.getOrNull()

    private fun toEntry(rich: RichLyricLine): Entry? {
        val text = rich.text?.trim()?.takeIf(String::isNotBlank) ?: return null
        val startMs = rich.begin.coerceAtLeast(0L)
        val line = LyricLine(
            text = text.take(MAX_TEXT_LENGTH),
            startMs = startMs,
            endMs = rich.end.coerceAtLeast(startMs),
            words = rich.words.orEmpty()
                .mapNotNull { word ->
                    val wordText = word.text?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    LyricWord(
                        text = wordText.take(120),
                        startMs = word.begin.coerceAtLeast(0L),
                        endMs = word.end.coerceAtLeast(word.begin.coerceAtLeast(0L)),
                    )
                }
                .take(256),
        )
        val translation = rich.translation
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { value ->
                LyricLine(
                    text = value.take(MAX_TEXT_LENGTH),
                    startMs = line.startMs,
                    endMs = line.endMs,
                    words = rich.translationWords.orEmpty()
                        .mapNotNull { word ->
                            val wordText = word.text?.takeIf(String::isNotBlank)
                                ?: return@mapNotNull null
                            LyricWord(
                                text = wordText.take(120),
                                startMs = word.begin.coerceAtLeast(0L),
                                endMs = word.end.coerceAtLeast(word.begin.coerceAtLeast(0L)),
                            )
                        }
                        .take(256),
                )
            }
        return Entry(line = line, translation = translation)
    }

    private fun parseLrc(content: String): Document? {
        val lines = LrcParser.parseLrc(content).take(MAX_LINES)
        if (lines.isEmpty()) return null
        return Document(entries = lines.map { Entry(it) })
    }
}
