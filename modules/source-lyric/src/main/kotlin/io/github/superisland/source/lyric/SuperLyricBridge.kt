package io.github.superisland.source.lyric

import android.os.Bundle
import android.media.MediaMetadata
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord
import java.util.LinkedHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Bounded SuperLyric (HChenX) Binder client.
 *
 * SuperLyric already owns player integration and sends the complete rich line over AIDL. We
 * consume that public contract instead of hooking individual players or scraping notifications.
 */
object SuperLyricBridge {
    private const val MAX_PUBLISHER_LENGTH = 128
    private const val MAX_METADATA_ENTRIES = 8
    private val lineListeners = CopyOnWriteArraySet<(LyricLine) -> Unit>()
    private val snapshotListeners = CopyOnWriteArraySet<(LyricSnapshot) -> Unit>()
    private val stateLock = Any()
    private val metadataByPublisher =
        object : LinkedHashMap<String, LyricMetadata>(MAX_METADATA_ENTRIES, 0.75f, true) {}

    private var activePublisher: String? = null

    @Volatile
    private var receiver: ISuperLyricReceiver? = null

    @Volatile
    var currentSnapshot: LyricSnapshot? = null
        private set

    fun addListener(listener: (LyricLine) -> Unit) {
        lineListeners += listener
    }

    fun removeListener(listener: (LyricLine) -> Unit) {
        lineListeners -= listener
    }

    fun addSnapshotListener(listener: (LyricSnapshot) -> Unit) {
        snapshotListeners += listener
        currentSnapshot?.let { snapshot -> runCatching { listener(snapshot) } }
    }

    fun removeSnapshotListener(listener: (LyricSnapshot) -> Unit) {
        snapshotListeners -= listener
    }

    val available: Boolean
        get() = runCatching { SuperLyricHelper.isAvailable() }.getOrDefault(false)

    fun start() {
        synchronized(stateLock) {
            if (receiver != null || !available) return
            val stub = object : ISuperLyricReceiver.Stub() {
                override fun onLyric(publisher: String, data: SuperLyricData) {
                    runCatching { dispatch(toSnapshot(publisher, data)) }
                }

                override fun onStop(publisher: String, data: SuperLyricData) {
                    runCatching { dispatchStop(publisher, data) }
                }
            }
            runCatching { SuperLyricHelper.registerReceiver(stub) }
                .onSuccess { receiver = stub }
                .onFailure { receiver = null }
        }
    }

    fun stop() {
        synchronized(stateLock) {
            receiver?.let { runCatching { SuperLyricHelper.unregisterReceiver(it) } }
            receiver = null
            currentSnapshot = null
            synchronized(stateLock) {
                activePublisher = null
                metadataByPublisher.clear()
            }
        }
    }

    private fun dispatch(snapshot: LyricSnapshot) {
        val normalized = snapshot.normalized()
        synchronized(stateLock) {
            activePublisher = normalized.publisher
            currentSnapshot = normalized
        }
        normalized.line?.let { line ->
            lineListeners.forEach { listener -> runCatching { listener(line) } }
        }
        snapshotListeners.forEach { listener -> runCatching { listener(normalized) } }
    }

    private fun dispatchStop(publisher: String, data: SuperLyricData?) {
        val publisherId = normalizePublisher(publisher)
        val previous: LyricSnapshot?
        synchronized(stateLock) {
            // A late stop from an old player must not tear down the current player's island.
            val currentPublisher = activePublisher
            if (currentPublisher != null && currentPublisher != publisherId) return
            previous = currentSnapshot
        }
        val metadata = synchronized(stateLock) { metadataByPublisher[publisherId] }
        val stopped = LyricSnapshot(
            publisher = publisherId,
            title = data?.title ?: metadata?.title ?: previous?.title,
            artist = data?.artist ?: metadata?.artist ?: previous?.artist,
            album = data?.album ?: metadata?.album ?: previous?.album,
            stopped = true,
            playback = (previous?.playback ?: LyricPlayback()).copy(isPlaying = false),
        ).normalized()
        synchronized(stateLock) {
            currentSnapshot = stopped
            activePublisher = null
            metadataByPublisher.remove(publisherId)
        }
        snapshotListeners.forEach { listener -> runCatching { listener(stopped) } }
    }

    private fun toSnapshot(publisher: String, data: SuperLyricData): LyricSnapshot {
        // PlaybackState/MediaMetadata were deprecated and made private in SuperLyricApi 3.3.
        // Keep the optional extra contract for publishers that provide an anchor, then let the
        // SystemUI host resolve the public MediaSession state when available.
        val publisherId = normalizePublisher(publisher)
        val previous = synchronized(stateLock) { currentSnapshot?.takeIf { it.publisher == publisherId } }
        val lyricText = data.lyric?.text?.trim()
        val mediaMetadata = data.mediaMetadataCompat()
        val metadata = synchronized(stateLock) {
            val prior = metadataByPublisher[publisherId]
            LyricMetadata(
                title = mediaMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?.takeIf { !it.isBlank() && it.trim() != lyricText }
                    ?: data.title?.takeIf { !it.isBlank() && it.trim() != lyricText }
                    ?: prior?.title,
                artist = mediaMetadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?.takeIf(String::isNotBlank)
                    ?: data.artist?.takeIf(String::isNotBlank)
                    ?: prior?.artist,
                album = mediaMetadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                    ?.takeIf(String::isNotBlank)
                    ?: data.album?.takeIf(String::isNotBlank)
                    ?: prior?.album,
            ).also {
                metadataByPublisher[publisherId] = it
                while (metadataByPublisher.size > MAX_METADATA_ENTRIES) {
                    val eldest = metadataByPublisher.entries.iterator().next().key
                    metadataByPublisher.remove(eldest)
                }
            }
        }
        val playback = data.extra.toPlayback(previous?.playback)
        val position = playback.positionMs
        return LyricSnapshot(
            publisher = publisherId,
            line = data.lyric?.toModel(position),
            secondary = data.secondary?.toModel(position),
            translation = data.translation?.toModel(position),
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            playback = playback,
        )
    }

    /** SuperLyricApi exposes this field in the parcel but omits a public getter. */
    private fun SuperLyricData.mediaMetadataCompat(): MediaMetadata? = runCatching {
        javaClass.getDeclaredField("mediaMetadata").apply { isAccessible = true }
            .get(this) as? MediaMetadata
    }.getOrNull()

    private fun normalizePublisher(value: String?): String =
        value.orEmpty().trim().take(MAX_PUBLISHER_LENGTH)

    @Suppress("DEPRECATION")
    private fun SuperLyricLine.toModel(positionMs: Long): LyricLine {
        val rawStart = startTime
        val rawEnd = endTime
        // Buffering events can carry zero timestamps. HyperLyric treats delay as the current line
        // duration; preserve that behavior without reading private player state.
        val inferredStart = if (rawStart == 0L && rawEnd == 0L && positionMs >= 0L) positionMs else rawStart
        val inferredEnd = when {
            rawEnd > inferredStart -> rawEnd
            delay > 0L -> inferredStart + delay
            else -> inferredStart
        }
        return LyricLine(
            text = text.orEmpty(),
            startMs = inferredStart,
            endMs = inferredEnd,
            words = words?.map { it.toModel() }.orEmpty(),
        )
    }

    private fun SuperLyricWord.toModel(): LyricWord =
        LyricWord(
            text = word.orEmpty(),
            startMs = startTime,
            endMs = endTime,
        )

    private fun Bundle?.toPlayback(previous: LyricPlayback?): LyricPlayback {
        val extras = this ?: return previous ?: LyricPlayback()
        val prior = previous ?: LyricPlayback()
        val positionKey = when {
            extras.containsKey("positionMs") -> "positionMs"
            extras.containsKey("position") -> "position"
            else -> null
        }
        val speedKey = when {
            extras.containsKey("playbackSpeed") -> "playbackSpeed"
            extras.containsKey("speed") -> "speed"
            else -> null
        }
        val playingKey = when {
            extras.containsKey("isPlaying") -> "isPlaying"
            extras.containsKey("playing") -> "playing"
            else -> null
        }
        val durationKey = when {
            extras.containsKey("durationMs") -> "durationMs"
            extras.containsKey("duration") -> "duration"
            else -> null
        }
        return LyricPlayback(
            positionMs = positionKey?.let { extras.getLong(it) } ?: prior.positionMs,
            speed = speedKey?.let { extras.getFloat(it) } ?: prior.speed,
            isPlaying = playingKey?.let { extras.getBoolean(it) } ?: prior.isPlaying,
            durationMs = durationKey?.let { extras.getLong(it) } ?: prior.durationMs,
        ).normalized()
    }
}

private data class LyricMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
)
