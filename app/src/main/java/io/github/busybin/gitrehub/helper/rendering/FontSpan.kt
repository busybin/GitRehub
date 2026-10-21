package io.github.busybin.gitrehub.helper.rendering

import android.text.TextPaint
import android.text.style.StyleSpan

/** [StyleSpan] that also scales the text and can tint it. */
class FontSpan(
    private val scale: Float,
    style: Int,
    private val color: Int = 0,
) : StyleSpan(style) {
    override fun updateMeasureState(paint: TextPaint) {
        super.updateMeasureState(paint)
        paint.textSize *= scale
    }

    override fun updateDrawState(paint: TextPaint) {
        super.updateDrawState(paint)
        paint.textSize *= scale
        if (color != 0) paint.color = color
    }
}
