package io.github.superisland.source.lyric

import android.os.Handler
import android.os.Looper

class LyricPositionController(
    private val onPosition: (Long) -> Unit,
    private val positionProvider: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val intervalMs: Long = 50L,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            onPosition(positionProvider())
            handler.postDelayed(this, intervalMs.coerceIn(16L, 500L))
        }
    }
    fun start() {
        handler.removeCallbacks(runnable)
        handler.post(runnable)
    }
    fun stop() { handler.removeCallbacks(runnable) }
}
