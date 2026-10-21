package io.github.busybin.gitrehub.provider.timeline.handler

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan
import androidx.annotation.ColorInt

/** Indents a quote and paints the vertical bar GitHub shows on the left. */
class MarkdownQuoteSpan(@param:ColorInt private val color: Int) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean) = BAR_WIDTH + BAR_GAP

    override fun drawLeadingMargin(
        canvas: Canvas,
        paint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout,
    ) {
        val previous = paint.color
        val previousStyle = paint.style
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRect(
            x.toFloat(),
            top.toFloat(),
            (x + dir * BAR_WIDTH).toFloat(),
            bottom.toFloat(),
            paint,
        )
        paint.color = previous
        paint.style = previousStyle
    }

    private companion object {
        const val BAR_WIDTH = 4
        const val BAR_GAP = 12
    }
}
