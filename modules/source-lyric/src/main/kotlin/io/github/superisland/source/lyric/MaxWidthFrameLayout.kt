package io.github.superisland.source.lyric

import android.content.Context
import android.widget.FrameLayout

/**
 * HyperLyric's injected Super Island wrapper.
 *
 * The SystemUI slot owns this outer measurement boundary. Keeping it separate from the lyric
 * renderer prevents OEM media-module rebuilds from shrinking two independently measured text
 * views into the same island width.
 */
class MaxWidthFrameLayout(context: Context) : FrameLayout(context) {

    /** Maximum width in pixels. A negative value keeps the host's original constraint. */
    var maxWidthPx: Int = -1

    /** Prevents the OEM reconciliation pass from hiding an attached injected slot. */
    var keepVisible: Boolean = false

    override fun setVisibility(visibility: Int) {
        if (keepVisible && visibility != VISIBLE) {
            super.setVisibility(VISIBLE)
            return
        }
        super.setVisibility(visibility)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val givenWidth = MeasureSpec.getSize(widthMeasureSpec)
        val newWidth =
            if (maxWidthPx > 0 && (givenWidth == 0 || givenWidth > maxWidthPx)) maxWidthPx else givenWidth
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.AT_MOST),
            heightMeasureSpec,
        )
        if (maxWidthPx > 0 && measuredWidth > maxWidthPx) {
            setMeasuredDimension(maxWidthPx, measuredHeight)
        }
    }
}
