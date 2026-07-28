package io.github.superisland.source.screenrecord

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * In-process mirror of [ScreenRecordingRuntimeStore].
 *
 * Evidence on warsaw: stop writes FINALIZING then IDLE to SharedPreferences and saves the MP4, but
 * the Compose detail page can remain stuck on「正在完成录屏」/红色「停止录制」because preference
 * listeners alone do not always refresh UI state. Same-process publish guarantees an immediate
 * update for the module detail page.
 */
object ScreenRecordingRuntimeHub {
    fun interface Listener {
        fun onRuntimeChanged(state: ScreenRecordingRuntimeState)
    }

    private val current = AtomicReference(ScreenRecordingRuntimeState())
    private val listeners = CopyOnWriteArrayList<Listener>()

    fun current(): ScreenRecordingRuntimeState = current.get()

    fun publish(state: ScreenRecordingRuntimeState) {
        current.set(state)
        for (listener in listeners) {
            runCatching { listener.onRuntimeChanged(state) }
        }
    }

    fun hydrateFrom(store: ScreenRecordingRuntimeStore): ScreenRecordingRuntimeState {
        val loaded = store.load()
        current.set(loaded)
        return loaded
    }

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
        runCatching { listener.onRuntimeChanged(current.get()) }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}
