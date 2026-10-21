package io.github.busybin.gitrehub.provider.timeline.handler

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.MetricAffectingSpan

/** Inline `code` / fenced blocks: monospace plus a tinted background per line. */
class CodeBackgroundRoundedSpan internal constructor(private val background: Int) :
    MetricAffectingSpan(), LeadingMarginSpan, LineBackgroundSpan {

    private val rect = RectF()

    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = Typeface.MONOSPACE
    }

    override fun updateDrawState(paint: TextPaint) {
        paint.typeface = Typeface.MONOSPACE
    }

    override fun drawBackground(
        canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int, baseline: Int,
        bottom: Int, text: CharSequence, start: Int, end: Int, line: Int,
    ) {
        val color = paint.color
        val style = paint.style
        paint.color = background
        paint.style = Paint.Style.FILL
        rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        canvas.drawRect(rect, paint)
        paint.color = color
        paint.style = style
    }

    override fun getLeadingMargin(first: Boolean) = INDENT

    override fun drawLeadingMargin(
        canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout,
    ) = Unit

    private companion object {
        const val INDENT = 30
    }
}
