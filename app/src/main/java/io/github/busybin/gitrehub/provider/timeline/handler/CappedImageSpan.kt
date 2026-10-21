package io.github.busybin.gitrehub.provider.timeline.handler

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan
import kotlin.math.roundToInt

/**
 * Clamps an image span's width to [maxWidth] — a table cell needs one-column-wide images.
 *
 * Subclasses [ImageSpan] because `Drawable.getBounds()` is final, so a wrapper cannot resize at
 * `getSize()` time; recomputed on every `getSize()` because images load asynchronously.
 */
internal class CappedImageSpan(
    drawable: Drawable,
    private val maxWidth: Int,
) : ImageSpan(drawable) {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        cap()
        return super.getSize(paint, text, start, end, fm)
    }

    private fun cap() {
        val drawable = drawable ?: return
        val bounds = drawable.bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        if (bounds.width() <= maxWidth) return
        val scale = maxWidth.toFloat() / bounds.width()
        drawable.setBounds(0, 0, maxWidth, (bounds.height() * scale).roundToInt())
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        cap()
        super.draw(canvas, text, start, end, x, top, y, bottom, paint)
    }
}
