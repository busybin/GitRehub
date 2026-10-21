package io.github.busybin.gitrehub.provider.timeline.handler.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.provider.timeline.MarkdownPalette
import kotlin.math.min

/** Failure red, readable on both light and dark themes (no Compose theme available here). */
private const val FAILED_TINT = 0xFFE5534B.toInt()

/**
 * Placeholder for a body image that has not been drawn yet: rounded box + glyph + label. Body images
 * load asynchronously and [UrlDrawable] draws nothing before Coil answers, so without this the text
 * does not even show that an image is there. Colors come from [MarkdownPalette]; the size is set by
 * the caller via `setBounds`.
 */
internal class ImageSlotDrawable(
    private val label: CharSequence,
    private val palette: MarkdownPalette,
    private val failed: Boolean,
    private val density: Float,
) : Drawable() {

    private val tint: Int = if (failed) FAILED_TINT else palette.text

    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(tint, 0x14)
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = ColorUtils.setAlphaComponent(tint, 0x33)
    }
    private val glyphFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(tint, 0x59)
    }
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        color = ColorUtils.setAlphaComponent(tint, 0x59)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
        color = ColorUtils.setAlphaComponent(if (failed) FAILED_TINT else palette.text, 0xB0)
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val radius = min(10f * density, min(w, h) / 6f)
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, background)
        val half = border.strokeWidth / 2f
        canvas.drawRoundRect(half, half, w - half, h - half, radius, radius, border)

        val textHeight = labelPaint.fontMetrics.let { it.descent - it.ascent }
        val glyphSize = min(40f * density, min(w * 0.22f, h * 0.34f))
        val gap = 6f * density
        // Too narrow or short for the label: draw the glyph only.
        val showLabel = glyphSize > 10f * density &&
            labelPaint.measureText(label, 0, label.length) <= w - 12f * density
        val blockHeight = if (showLabel) glyphSize + gap + textHeight else glyphSize
        val top = ((h - blockHeight) / 2f).coerceAtLeast(4f * density)
        val glyphLeft = (w - glyphSize) / 2f
        drawGlyph(canvas, RectF(glyphLeft, top, glyphLeft + glyphSize, top + glyphSize))
        if (showLabel) {
            val baseline = top + glyphSize + gap - labelPaint.fontMetrics.ascent
            canvas.drawText(label, 0, label.length, w / 2f, baseline, labelPaint)
        }
    }

    /** Hand-drawn "image" glyph: frame + hill + sun. */
    private fun drawGlyph(canvas: Canvas, rect: RectF) {
        val corner = rect.width() * 0.14f
        canvas.drawRoundRect(rect, corner, corner, glyphStroke)
        val inset = rect.width() * 0.18f
        val left = rect.left + inset
        val right = rect.right - inset
        val top = rect.top + inset
        val bottom = rect.bottom - inset
        val width = right - left
        val height = bottom - top
        if (width <= 0f || height <= 0f) return
        val path = Path().apply {
            moveTo(left, bottom)
            lineTo(left + width * 0.34f, top + height * 0.42f)
            lineTo(left + width * 0.62f, bottom)
            close()
        }
        canvas.drawPath(path, glyphFill)
        canvas.drawCircle(right - width * 0.22f, top + height * 0.26f, width * 0.12f, glyphFill)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /** Image not loaded yet: placeholder that also reserves the space. */
        fun loading(context: Context, palette: MarkdownPalette, widthPx: Int): ImageSlotDrawable =
            sized(context, palette, false, R.string.m3_image_loading, widthPx)

        /** Load failed: show a placeholder instead of blank space. */
        fun failed(context: Context, palette: MarkdownPalette, widthPx: Int): ImageSlotDrawable =
            sized(context, palette, true, R.string.m3_image_failed, widthPx)

        private fun sized(
            context: Context,
            palette: MarkdownPalette,
            failed: Boolean,
            @androidx.annotation.StringRes label: Int,
            widthPx: Int,
        ): ImageSlotDrawable {
            val density = context.resources.displayMetrics.density
            val drawable = ImageSlotDrawable(context.getString(label), palette, failed, density)
            val width = widthPx.coerceAtLeast((140f * density).toInt())
            // Image-like aspect ratio: too short reads as a sliver, too tall makes the text jump.
            val height = (width * 0.42f).coerceIn(96f * density, 220f * density).toInt()
            drawable.setBounds(0, 0, width, height)
            return drawable
        }
    }
}
