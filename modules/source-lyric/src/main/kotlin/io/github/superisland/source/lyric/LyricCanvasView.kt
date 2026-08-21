package io.github.superisland.source.lyric

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Minimal Canvas逐字 view for island, to be injected into SystemUI.
 * Real HyperLyric RichLyricLineView will be ported here.
 */
class LyricCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f }
    private var line: LyricLine? = null
    private var positionMs: Long = 0L

    fun setLyric(line: LyricLine, positionMs: Long) {
        this.line = line
        this.positionMs = positionMs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val text = line?.text ?: return
        // TODO:逐字高亮 via WordSyncRenderer
        canvas.drawText(text, 0f, height / 2f, paint)
    }
}
