package io.github.superisland.source.lyric

import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Real SuperLyric (HChenX) Binder client via SuperLyricApi AIDL.
 * Registers an ISuperLyricReceiver so SuperLyric pushes lyric lines over Binder.
 * Non-invasive: no Hook into players, no ContentResolver scraping.
 */
object SuperLyricBridge {
    private val listeners = CopyOnWriteArraySet<(LyricLine) -> Unit>()
    private var receiver: ISuperLyricReceiver? = null

    fun addListener(listener: (LyricLine) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (LyricLine) -> Unit) {
        listeners -= listener
    }

    val available: Boolean
        get() = runCatching { SuperLyricHelper.isAvailable() }.getOrDefault(false)

    fun start() {
        if (receiver != null) return
        if (!available) return
        val stub =
            object : ISuperLyricReceiver.Stub() {
                override fun onLyric(publisher: String, data: SuperLyricData) {
                    val line = data.lyric?.toModel() ?: return
                    listeners.forEach { it(line) }
                }

                override fun onStop(publisher: String, data: SuperLyricData) = Unit
            }
        runCatching { SuperLyricHelper.registerReceiver(stub) }
            .onSuccess { receiver = stub }
            .onFailure { receiver = null }
    }

    fun stop() {
        receiver?.let { runCatching { SuperLyricHelper.unregisterReceiver(it) } }
        receiver = null
    }

    private fun SuperLyricLine.toModel(): LyricLine {
        val wordModels = words?.map { it.toModel() }.orEmpty()
        return LyricLine(
            text = text,
            startMs = startTime,
            endMs = endTime,
            words = wordModels,
        )
    }

    private fun SuperLyricWord.toModel(): LyricWord =
        LyricWord(
            text = word,
            startMs = startTime,
            endMs = endTime,
        )
}