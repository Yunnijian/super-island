package io.github.superisland.source.lyric

import android.os.Handler
import android.os.Looper

class LyricPositionController(
    private val onPosition: (Long) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            onPosition(System.currentTimeMillis())
            handler.postDelayed(this, 200L)
        }
    }
    fun start() { handler.post(runnable) }
    fun stop() { handler.removeCallbacks(runnable) }
}
