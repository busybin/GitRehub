package io.github.busybin.gitrehub.provider.timeline.handler

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.style.LineHeightSpan
import android.text.style.ReplacementSpan

/** Draws a horizontal rule across the given [width] and shrinks the line box around it. */
class HrSpan internal constructor(private val color: Int, private val width: Int) :
    ReplacementSpan(), LineHeightSpan {

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
        paint.measureText(text, start, end).toInt()

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int,
        y: Int, bottom: Int, paint: Paint,
    ) {
        val previous = paint.color
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRect(Rect(0, bottom - THICKNESS, x.toInt() + width, bottom), paint)
        paint.color = previous
    }

    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int, fm: Paint.FontMetricsInt,
    ) {
        fm.top /= 3
        fm.ascent /= 3
        fm.bottom /= 3
        fm.descent /= 3
    }

    private companion object {
        const val THICKNESS = 10
    }
}
