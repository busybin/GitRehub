package io.github.busybin.gitrehub.provider.timeline.handler.drawable

import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.text.Html.ImageGetter
import android.widget.TextView
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.provider.timeline.MarkdownPalette
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * Loads body images through Coil and draws a space-reserving placeholder until each one arrives.
 * The target text view is held weakly: a click may leave the screen while a request is in flight.
 *
 * [vector] images are SVGs (shields.io badges): their intrinsic size is in CSS pixels, so they are
 * re-decoded at the screen density instead of staying a few dozen raw pixels tall. [inline] images
 * share a line with text or other badges and get a compact alt-text placeholder.
 */
class DrawableGetter(
    textView: TextView,
    private val maxWidth: Int,
    private val palette: MarkdownPalette,
    private val vector: Boolean = false,
    private val inline: Boolean = false,
    private val alt: String? = null,
) : ImageGetter, Drawable.Callback {

    private val target = WeakReference(textView)

    /** Set when the SVG's own width/height were read, so the decoded size needs no clamping. */
    private var exact = false

    override fun getDrawable(url: String): Drawable {
        val slot = UrlDrawable()
        val textView = target.get() ?: return slot
        slot.setPlaceholder(
            if (inline) {
                ImageSlotDrawable.inline(textView.context, palette, alt, maxWidth)
            } else {
                ImageSlotDrawable.loading(textView.context, palette, maxWidth)
            }
        )
        slot.callback = this
        if (vector) {
            // The size that Coil reports for an SVG depends on whether the file has a viewBox,
            // so read width/height from the markup and decode at exactly that size instead.
            Thread {
                val size = runCatching { svgSize(url) }.getOrNull()
                if (size == null) {
                    enqueue(url, textView, slot, null)
                } else {
                    val density = textView.resources.displayMetrics.density
                    val width = (size.first * density).roundToInt().coerceIn(1, maxWidth)
                    val height = (width * size.second / size.first).roundToInt().coerceAtLeast(1)
                    exact = true
                    enqueue(url, textView, slot, Size(width, height))
                }
            }.apply { isDaemon = true }.start()
            return slot
        }
        enqueue(url, textView, slot, null)
        return slot
    }

    /** CSS pixel size of an SVG: its width/height attributes, else its viewBox. */
    private fun svgSize(url: String): Pair<Float, Float>? {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        val tag = Regex("<svg[^>]*>").find(text)?.value ?: return null
        fun attribute(name: String): String? =
            Regex("\\b$name\\s*=\\s*[\"']([^\"']+)[\"']").find(tag)?.groupValues?.get(1)
        val width = attribute("width")?.toFloatOrNull()
        val height = attribute("height")?.toFloatOrNull()
        if (width != null && height != null && width > 0 && height > 0) return width to height
        val box = attribute("viewBox")?.trim()?.split(Regex("[\\s,]+"))
            ?.mapNotNull { it.toFloatOrNull() }
        if (box != null && box.size == 4 && box[2] > 0 && box[3] > 0) return box[2] to box[3]
        return null
    }

    private fun enqueue(url: String, textView: TextView, slot: UrlDrawable, size: Size?) {
        val builder = ImageRequest.Builder(textView.context)
            .data(url)
            // Animated GIFs cannot be drawn on a hardware canvas.
            .allowHardware(false)
            .target(
                onSuccess = { picture -> onSuccess(url, textView, slot, picture, size != null) },
                onError = { applyFailure(slot, textView) },
            )
        if (size != null) builder.size(size)
        textView.context.imageLoader.enqueue(builder.build())
    }

    /** Vector images are decoded a second time at density-scaled size (first pass = intrinsic px). */
    private fun onSuccess(
        url: String,
        textView: TextView,
        slot: UrlDrawable,
        picture: Drawable,
        scaled: Boolean,
    ) {
        val intrinsicWidth = picture.intrinsicWidth
        val intrinsicHeight = picture.intrinsicHeight
        if (vector && !scaled && intrinsicWidth > 0 && intrinsicHeight > 0) {
            val density = textView.resources.displayMetrics.density
            val width = (intrinsicWidth * density).roundToInt().coerceIn(1, maxWidth)
            val height = (width * intrinsicHeight.toFloat() / intrinsicWidth).roundToInt().coerceAtLeast(1)
            enqueue(url, textView, slot, Size(width, height))
            return
        }
        applyImage(slot, textView, picture)
    }

    private fun applyImage(slot: UrlDrawable, textView: TextView, picture: Drawable) {
        textView.post {
            val intrinsicWidth = picture.intrinsicWidth.coerceAtLeast(1)
            val intrinsicHeight = picture.intrinsicHeight.coerceAtLeast(1)
            val rect = if (vector && exact) {
                val width = intrinsicWidth.coerceAtMost(maxWidth)
                val height = (width * intrinsicHeight.toFloat() / intrinsicWidth).roundToInt().coerceAtLeast(1)
                Rect(0, 0, width, height)
            } else if (vector) {
                // A badge is ~20 CSS px tall, but the decoded bitmap may be in CSS px or in device
                // px depending on whether the SVG carries a viewBox. Cap the height instead of
                // trusting the width: an oversized bitmap then shrinks back to badge size.
                val density = textView.resources.displayMetrics.density
                var height = intrinsicHeight.coerceAtMost((28f * density).roundToInt().coerceAtLeast(1))
                var width = (intrinsicWidth.toFloat() * height / intrinsicHeight).roundToInt().coerceAtLeast(1)
                if (width > maxWidth) {
                    width = maxWidth
                    height = (intrinsicHeight.toFloat() * width / intrinsicWidth).roundToInt().coerceAtLeast(1)
                }
                Rect(0, 0, width, height)
            } else {
                val scale = if (intrinsicWidth > maxWidth) intrinsicWidth.toFloat() / maxWidth else 1f
                // 1.3: generous enough that wide screenshots do not dominate the text.
                Rect(
                    0,
                    0,
                    (intrinsicWidth / scale / 1.3f).roundToInt(),
                    (intrinsicHeight / scale / 1.3f).roundToInt(),
                )
            }
            picture.bounds = rect
            slot.bounds = rect
            slot.setImage(picture)
            slot.setPlaceholder(null)
            if (picture is Animatable) picture.start()
            textView.text = textView.text
            textView.invalidate()
        }
    }

    private fun applyFailure(slot: UrlDrawable, textView: TextView) {
        textView.post {
            slot.setImage(null)
            slot.setPlaceholder(
                if (inline) {
                    ImageSlotDrawable.inline(textView.context, palette, alt, maxWidth, failed = true)
                } else {
                    ImageSlotDrawable.failed(textView.context, palette, maxWidth)
                }
            )
            textView.text = textView.text
            textView.invalidate()
        }
    }

    override fun invalidateDrawable(drawable: Drawable) {
        target.get()?.invalidate()
    }

    override fun scheduleDrawable(drawable: Drawable, runnable: Runnable, whenMillis: Long) = Unit

    override fun unscheduleDrawable(drawable: Drawable, runnable: Runnable) = Unit

    init {
        textView.setTag(R.id.drawable_callback, this)
    }
}
