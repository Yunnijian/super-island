package io.github.superisland.source.lyric

import android.content.Context
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ConnectionListener
import io.github.proify.lyricon.subscriber.LyriconFactory
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.subscriber.ProviderInfo
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The Lyricon source adapter copied from HyperLyric's subscriber lifecycle.
 *
 * Lyricon is a separate provider service, so querying MediaSession metadata is not equivalent:
 * it loses rich words, translations and provider-specific timing. This bridge keeps the public
 * Lyricon subscriber alive in SystemUI and exposes the same normalized snapshot contract used by
 * SuperLyric and LyricInfo.
 */
object LyriconBridge {
    private const val MAX_PUBLISHER_LENGTH = 128
    private const val MAX_LINES = 4096
    private const val MIN_DELAY_MS = -5000
    private const val MAX_DELAY_MS = 5000

    private val listeners = CopyOnWriteArraySet<(LyricSnapshot) -> Unit>()
    private val lock = Any()

    @Volatile
    private var subscriber: LyriconSubscriber? = null

    @Volatile
    private var currentSnapshot: LyricSnapshot? = null

    @Volatile
    private var activeProviderPackage: String? = null

    @Volatile
    private var activePlayerPackage: String? = null

    @Volatile
    private var defaultDelayMs: Int = 0

    @Volatile
    private var providerDelays: Map<String, Int> = emptyMap()

    private var song: Song? = null
    private var playback = LyricPlayback()

    fun addSnapshotListener(listener: (LyricSnapshot) -> Unit) {
        listeners += listener
        currentSnapshot?.let { snapshot -> runCatching { listener(snapshot) } }
    }

    fun removeSnapshotListener(listener: (LyricSnapshot) -> Unit) {
        listeners -= listener
    }

    val available: Boolean
        get() = subscriber != null || runCatching { LyriconFactory }.isSuccess

    /** Starts the exact Lyricon Central subscription lifecycle used by HyperLyric. */
    fun start(context: Context, delays: Map<String, Int> = emptyMap(), defaultDelay: Int = 0) {
        configure(delays, defaultDelay)
        synchronized(lock) {
            if (subscriber != null) return
            val created = runCatching { LyriconFactory.createSubscriber(context.applicationContext) }
                .getOrNull() ?: return
            subscriber = created
            created.addConnectionListener(connectionListener)
            if (!created.subscribeActivePlayer(activePlayerListener)) {
                runCatching { created.destroy() }
                subscriber = null
                return
            }
            runCatching { created.register() }.onFailure {
                runCatching { created.unsubscribeActivePlayer(activePlayerListener) }
                runCatching { created.destroy() }
                subscriber = null
            }
        }
    }

    fun configure(delays: Map<String, Int> = emptyMap(), defaultDelay: Int = 0) {
        defaultDelayMs = defaultDelay.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
        providerDelays = delays.asSequence()
            .filter { it.key.isNotBlank() }
            .take(64)
            .associate { it.key.take(MAX_PUBLISHER_LENGTH) to it.value.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS) }
        activeProviderPackage?.let { provider ->
            // Re-emit the current line immediately when its per-provider delay changes.
            currentSnapshot?.let { emit(buildSnapshot(playback.positionMs)) }
        }
    }

    fun stop() {
        synchronized(lock) {
            subscriber?.let { sub ->
                runCatching { sub.unsubscribeActivePlayer(activePlayerListener) }
                runCatching { sub.removeConnectionListener(connectionListener) }
                runCatching { sub.unregister() }
                runCatching { sub.destroy() }
            }
            subscriber = null
            song = null
            playback = LyricPlayback()
            activeProviderPackage = null
            activePlayerPackage = null
            val stopped = currentSnapshot?.copy(stopped = true, playback = playback)
            currentSnapshot = stopped
            stopped?.let { emit(it) }
            currentSnapshot = null
        }
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnected(subscriber: LyriconSubscriber) = Unit
        override fun onReconnected(subscriber: LyriconSubscriber) = Unit
        override fun onDisconnected(subscriber: LyriconSubscriber) = Unit
        override fun onConnectTimeout(subscriber: LyriconSubscriber) = Unit
    }

    private val activePlayerListener = object : ActivePlayerListener {
        override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
            synchronized(lock) {
                song = null
                playback = LyricPlayback()
                activeProviderPackage = providerInfo?.providerPackageName.normalizePackage()
                activePlayerPackage = providerInfo?.playerPackageName.normalizePackage()
                currentSnapshot = null
            }
            listeners.forEach { listener -> runCatching { listener(stoppedSnapshot()) } }
        }

        override fun onSongChanged(song: Song?) {
            synchronized(lock) {
                this@LyriconBridge.song = song?.normalizeForBridge()
                playback = playback.copy(
                    durationMs = song?.duration?.takeIf { it > 0L } ?: playback.durationMs,
                ).normalized()
            }
            emit(buildSnapshot(playback.positionMs))
        }

        override fun onReceiveText(text: String?) {
            val value = text?.trim()?.takeIf(String::isNotBlank) ?: return
            val position = synchronized(lock) { playback.positionMs }
            val line = LyricLine(value.take(400), position, position + 5000L)
            val snapshot = LyricSnapshot(
                publisher = activePlayerPackage.orEmpty(),
                line = line,
                title = song?.name,
                artist = song?.artist,
                playback = playback,
            ).normalized()
            synchronized(lock) { currentSnapshot = snapshot }
            emit(snapshot)
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            synchronized(lock) { playback = playback.copy(isPlaying = isPlaying).normalized() }
            emit(buildSnapshot(playback.positionMs))
        }

        override fun onPositionChanged(position: Long) {
            val adjusted = (position - activeDelay()).coerceAtLeast(0L)
            synchronized(lock) { playback = playback.copy(positionMs = adjusted).normalized() }
            emit(buildSnapshot(adjusted))
        }

        override fun onSeekTo(position: Long) = onPositionChanged(position)
        override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit
        override fun onDisplayRomaChanged(isDisplayRoma: Boolean) = Unit
    }

    private fun activeDelay(): Int = providerDelays[activeProviderPackage] ?: defaultDelayMs

    private fun buildSnapshot(positionMs: Long): LyricSnapshot {
        val currentSong = synchronized(lock) { song }
        val lines = currentSong?.lyrics.orEmpty().take(MAX_LINES)
        val index = lines.indexOfLast { it.begin <= positionMs }
        val selected = lines.getOrNull(index.coerceAtLeast(0))
        val next = lines.getOrNull(index + 1)
        val snapshot = LyricSnapshot(
            publisher = activePlayerPackage.orEmpty(),
            line = selected?.toLocalLine(),
            secondary = next?.toLocalLine(),
            translation = selected?.translationLine(),
            title = currentSong?.name,
            artist = currentSong?.artist,
            album = currentSong?.metadata?.getString("album")
                ?: currentSong?.metadata?.getString("albumName"),
            playback = synchronized(lock) { playback },
        ).normalized()
        synchronized(lock) { currentSnapshot = snapshot }
        return snapshot
    }

    private fun stoppedSnapshot(): LyricSnapshot = LyricSnapshot(
        publisher = activePlayerPackage.orEmpty(),
        title = song?.name,
        artist = song?.artist,
        album = song?.metadata?.getString("album"),
        playback = LyricPlayback(),
        stopped = true,
    ).normalized()

    private fun emit(snapshot: LyricSnapshot) {
        listeners.forEach { listener -> runCatching { listener(snapshot) } }
    }

    private fun Song.normalizeForBridge(): Song = runCatching { normalize() }.getOrDefault(this)

    private fun RichLyricLine.toLocalLine(): LyricLine? {
        val textValue = text.orEmpty().trim()
        val localWords = words.orEmpty().mapNotNull { word ->
            val wordText = word.text.orEmpty()
            if (wordText.isBlank()) null else LyricWord(wordText, word.begin, word.end)
        }
        if (textValue.isBlank() && localWords.isEmpty()) return null
        return LyricLine(
            text = if (textValue.isNotBlank()) textValue else localWords.joinToString("") { it.text },
            startMs = begin,
            endMs = end.takeIf { it > begin } ?: (begin + duration.coerceAtLeast(1L)),
            words = localWords,
            isTitleLine = metadata?.getString("title_line") == "true",
        )
    }

    private fun RichLyricLine.translationLine(): LyricLine? {
        val value = translation.orEmpty().trim()
        val wordsValue = translationWords.orEmpty().mapNotNull { word ->
            val textValue = word.text.orEmpty()
            if (textValue.isBlank()) null else LyricWord(textValue, word.begin, word.end)
        }
        if (value.isBlank() && wordsValue.isEmpty()) return null
        return LyricLine(
            text = if (value.isNotBlank()) value else wordsValue.joinToString("") { it.text },
            startMs = begin,
            endMs = end.takeIf { it > begin } ?: (begin + duration.coerceAtLeast(1L)),
            words = wordsValue,
        )
    }

    private fun String?.normalizePackage(): String? = this
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.take(MAX_PUBLISHER_LENGTH)
}
