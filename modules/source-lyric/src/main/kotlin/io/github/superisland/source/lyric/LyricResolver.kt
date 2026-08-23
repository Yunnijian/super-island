package io.github.superisland.source.lyric

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import io.github.superisland.source.lyric.hyperlyric.common.color.ColorExtractor

/**
 * MediaSession lyricInfo fallback. SuperLyric pushes via Binder listener directly.
 */
object LyricResolver {
    private const val MAX_LYRIC_METADATA_LENGTH = 256_000
    private val lyricKeys = listOf(
        "lyricInfo",
        "lyrics",
        "lyric",
        "android.media.metadata.LYRICS",
    )

    fun resolve(context: Context, controllers: List<MediaController>): LyricLine? {
        controllers.forEach { controller ->
            val metadata = controller.metadata ?: return@forEach
            lyricPayloads(metadata).forEach { raw ->
                LyricDocumentParser.parse(raw)?.lines?.firstOrNull()?.let { return it }
            }
        }
        return null
    }

    /** Resolves a complete source-independent snapshot from public MediaSession state. */
    fun resolveSnapshot(context: Context, publisherHint: String? = null): LyricSnapshot? =
        runCatching {
            val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
            val controllers = manager.getActiveSessions(null)
            val hinted = publisherHint?.takeIf(String::isNotBlank)
            val controller = controllers.firstOrNull { hinted != null && it.packageName == hinted }
                ?: controllers.firstOrNull { isPlaying(it) }
                ?: controllers.firstOrNull()
                ?: return null
            val metadata = controller.metadata
            val playback = playback(controller)
            val document = metadata?.let(::lyricDocument)
            val artworkColors = metadata?.let(::artworkColors).orEmpty()
            val metadataSnapshot = document?.let {
                snapshotForDocument(
                    document = it,
                    publisher = controller.packageName.orEmpty(),
                    playback = playback,
                    title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                )?.copy(artworkColors = artworkColors)
            }
            val raw = metadata?.let(::lyricText)
            val line = metadataSnapshot?.line
                ?: raw?.takeIf { it.isNotBlank() }?.let {
                    LyricLine(
                        text = it.lineSequence().firstOrNull().orEmpty().trim().take(400),
                        startMs = playback.positionMs,
                        endMs = playback.positionMs + 5_000L,
                    ).takeIf { candidate -> candidate.text.isNotBlank() }
                }
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            if (metadataSnapshot == null && line == null && title.isNullOrBlank() &&
                artist.isNullOrBlank() && album.isNullOrBlank()
            ) {
                return null
            }
            metadataSnapshot ?: LyricSnapshot(
                publisher = controller.packageName.orEmpty(),
                line = line,
                title = title,
                artist = artist,
                album = album,
                artworkColors = artworkColors,
                playback = playback,
            ).normalized()
        }.getOrNull()

    /** Pure selection/mapping entry shared by the MediaSession resolver and unit tests. */
    internal fun snapshotForDocument(
        document: LyricDocumentParser.Document,
        publisher: String,
        playback: LyricPlayback,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
    ): LyricSnapshot? {
        if (document.entries.isEmpty()) return null
        val index = selectEntryIndex(document.entries, playback.positionMs)
        val entry = document.entries[index]
        return LyricSnapshot(
            publisher = publisher,
            line = entry.line,
            // HyperLyric promotes the next timed line as a preview when translation is not used.
            secondary = document.entries.getOrNull(index + 1)?.line,
            translation = entry.translation,
            title = document.title ?: title,
            artist = document.artist ?: artist,
            album = document.album ?: album,
            playback = playback,
        ).normalized()
    }

    private fun lyricDocument(metadata: MediaMetadata): LyricDocumentParser.Document? = lyricPayloads(metadata)
        .mapNotNull { raw -> LyricDocumentParser.parse(raw) }
        .firstOrNull()

    private fun lyricText(metadata: MediaMetadata): String? = lyricPayloads(metadata)
        .firstOrNull()

    private fun lyricPayloads(metadata: MediaMetadata): Sequence<String> = lyricKeys
        .asSequence()
        .mapNotNull { key -> metadata.getString(key) ?: metadata.getText(key)?.toString() }
        .map { it.take(MAX_LYRIC_METADATA_LENGTH) }
        .filter { it.isNotBlank() }

    private fun artworkColors(metadata: MediaMetadata): List<Int> = runCatching {
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: return@runCatching emptyList()
        ColorExtractor.extractThemePalette(bitmap, maxColors = 4).onBlackBackground
    }.getOrDefault(emptyList())

    private fun selectEntryIndex(
        entries: List<LyricDocumentParser.Entry>,
        positionMs: Long,
    ): Int {
        if (entries.isEmpty()) return -1
        return entries.indexOfLast { positionMs >= it.line.startMs && positionMs < it.line.endMs }
            .takeIf { it >= 0 }
            ?: entries.indexOfLast { it.line.startMs <= positionMs }
                .takeIf { it >= 0 }
            ?: 0
    }

    private fun isPlaying(controller: MediaController): Boolean =
        controller.playbackState?.state == PlaybackState.STATE_PLAYING ||
            controller.playbackState?.state == PlaybackState.STATE_BUFFERING

    private fun playback(controller: MediaController): LyricPlayback {
        val state = controller.playbackState
        val metadata = controller.metadata
        val position = state?.position?.takeIf { it >= 0L } ?: 0L
        val speed = state?.playbackSpeed?.takeIf { it.isFinite() && it >= 0f } ?: 1f
        return LyricPlayback(
            positionMs = position,
            speed = speed,
            isPlaying = isPlaying(controller),
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                ?.takeIf { it > 0L } ?: 0L,
        ).normalized()
    }
}
