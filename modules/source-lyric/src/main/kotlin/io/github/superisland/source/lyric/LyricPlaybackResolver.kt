package io.github.superisland.source.lyric

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import io.github.superisland.source.lyric.hyperlyric.common.color.ColorExtractor

/** Public MediaSession fallback used because SuperLyricApi no longer exposes playback fields. */
object LyricPlaybackResolver {
    fun resolve(context: Context, publisher: String, fallback: LyricPlayback): LyricPlayback {
        return resolveWithMetadata(context, publisher, fallback).playback
    }

    /** Resolves the public MediaSession state and the metadata used by HyperLyric's info row. */
    fun resolveWithMetadata(
        context: Context,
        publisher: String,
        fallback: LyricPlayback,
    ): LyricPlaybackResolution {
        if (publisher.isBlank()) return LyricPlaybackResolution(fallback)
        val publisherPackage = publisher.substringBefore('/').substringBefore(':').trim()
        if (publisherPackage.isBlank()) return LyricPlaybackResolution(fallback)
        return runCatching {
            val manager = context.getSystemService(MediaSessionManager::class.java)
                ?: return LyricPlaybackResolution(fallback)
            val controller = manager.getActiveSessions(null)
                .firstOrNull { it.packageName == publisherPackage }
                ?: return LyricPlaybackResolution(fallback)
            val state = controller.playbackState ?: return LyricPlaybackResolution(fallback)
            val metadata = controller.metadata
            val playing = state.state == PlaybackState.STATE_PLAYING ||
                state.state == PlaybackState.STATE_BUFFERING
            LyricPlaybackResolution(
                playback = LyricPlayback(
                    positionMs = state.position.takeIf { it >= 0L } ?: fallback.positionMs,
                    speed = state.playbackSpeed.takeIf { it.isFinite() && it >= 0f } ?: fallback.speed,
                    isPlaying = playing,
                    durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                        ?.takeIf { it > 0L } ?: fallback.durationMs,
                ).normalized(),
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            )
        }.getOrElse { LyricPlaybackResolution(fallback) }
    }

    /** Reads the active publisher's artwork palette for HyperLyric adaptive text styles. */
    fun artworkColors(context: Context, publisher: String): List<Int> {
        if (publisher.isBlank()) return emptyList()
        val publisherPackage = publisher.substringBefore('/').substringBefore(':').trim()
        if (publisherPackage.isBlank()) return emptyList()
        return runCatching {
            val manager = context.getSystemService(MediaSessionManager::class.java)
                ?: return emptyList()
            val metadata = manager.getActiveSessions(null)
                .firstOrNull { it.packageName == publisherPackage }
                ?.metadata
                ?: return emptyList()
            val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: return emptyList()
            ColorExtractor.extractThemePalette(bitmap, maxColors = 4).onBlackBackground
        }.getOrDefault(emptyList())
    }
}

data class LyricPlaybackResolution(
    val playback: LyricPlayback,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
)
