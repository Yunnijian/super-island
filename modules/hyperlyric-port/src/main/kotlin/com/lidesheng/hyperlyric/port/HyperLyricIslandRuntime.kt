package com.lidesheng.hyperlyric.port

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.island.renderer.BaseIslandRenderer
import io.github.libxposed.api.XposedModule
import io.github.superisland.source.lyric.LyricLine
import io.github.superisland.source.lyric.LyricSnapshot

/**
 * Thin compatibility adapter for the fixed HyperLyric runtime.
 *
 * All normal lyric-island ownership remains in HyperLyric's RootLyricSink, BaseIslandRenderer
 * and root/island packages. This class only supplies Super Island's LSPosed module and
 * RemotePreferences to the unchanged upstream package-load chain. Its fallback submission is
 * used exclusively by the separately selected MEDIA_FALLBACK compatibility source.
 */
object HyperLyricIslandRuntime {
    private const val SOURCE_ID = "super-island-host"

    @Volatile
    private var preferences: SharedPreferences? = null

    @JvmStatic
    fun prepare(
        module: XposedModule,
        remotePreferences: SharedPreferences,
        classLoader: ClassLoader,
    ) {
        preferences = remotePreferences
        HookEntry.preparePortRuntime(module, remotePreferences, classLoader)
    }

    @JvmStatic
    fun clear() {
        BaseIslandRenderer.clearAllViews()
        LyriconDataBridge.clearState()
    }

    @JvmStatic
    fun refreshSettings() {
        val enabled = preferences?.getBoolean("key_hook_enable_super_island", false) == true
        if (enabled) {
            BaseIslandRenderer.refreshActiveIsland()
        } else {
            clear()
        }
    }

    @JvmStatic
    fun hasAttachedNativeSlot(): Boolean =
        IslandPresentationCoordinator.snapshotAttachedInjectedHosts().isNotEmpty()

    @JvmStatic
    fun submitFallbackSnapshot(snapshot: LyricSnapshot) {
        if (snapshot.stopped) {
            clear()
            return
        }
        val publisher = snapshot.publisher.trim()
        if (publisher.isEmpty()) return

        val primary = snapshot.line?.toUpstreamLine(
            secondary = snapshot.secondary,
            translation = snapshot.translation,
        )
        LyriconDataBridge.updateLyricPackage(publisher)
        LyriconDataBridge.updateMediaMetadata(
            LyricMediaMetadata(
                sourceId = SOURCE_ID,
                packageName = publisher,
                title = snapshot.title?.takeUnless { it == snapshot.line?.text },
                artist = snapshot.artist,
                album = snapshot.album,
                duration = snapshot.playback.durationMs.takeIf { it > 0L },
            ),
        )
        if (primary != null) {
            LyriconDataBridge.updateLyricLine(primary)
            LyriconDataBridge.currentNextLyricLine = snapshot.secondary?.toUpstreamLine()
            BaseIslandRenderer.updateLyricLine()
        }
        LyriconDataBridge.currentPosition = snapshot.playback.positionMs
        BaseIslandRenderer.onPlaybackStateChanged(snapshot.playback.isPlaying)
        BaseIslandRenderer.updateMetadata()
        BaseIslandRenderer.updatePosition(snapshot.playback.positionMs, snapshot.playback.speed)
    }

    private fun LyricLine.toUpstreamLine(
        secondary: LyricLine? = null,
        translation: LyricLine? = null,
    ): RichLyricLine = RichLyricLine(
        begin = startMs,
        end = endMs.coerceAtLeast(startMs),
        duration = (endMs - startMs).coerceAtLeast(0L),
        text = text,
        words = words.map { word ->
            LyricWord(
                begin = word.startMs,
                end = word.endMs.coerceAtLeast(word.startMs),
                duration = (word.endMs - word.startMs).coerceAtLeast(0L),
                text = word.text,
            )
        },
        secondary = secondary?.text,
        secondaryWords = secondary?.words?.map { word ->
            LyricWord(
                begin = word.startMs,
                end = word.endMs.coerceAtLeast(word.startMs),
                duration = (word.endMs - word.startMs).coerceAtLeast(0L),
                text = word.text,
            )
        },
        translation = translation?.text,
        translationWords = translation?.words?.map { word ->
            LyricWord(
                begin = word.startMs,
                end = word.endMs.coerceAtLeast(word.startMs),
                duration = (word.endMs - word.startMs).coerceAtLeast(0L),
                text = word.text,
            )
        },
    )
}
