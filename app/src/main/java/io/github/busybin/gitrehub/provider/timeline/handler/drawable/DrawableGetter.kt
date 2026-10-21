package io.github.busybin.gitrehub.provider.timeline.handler.drawable

import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.text.Html.ImageGetter
import android.widget.TextView
import coil.imageLoader
import coil.request.ImageRequest
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.provider.timeline.MarkdownPalette
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * Loads body images through Coil and draws a space-reserving placeholder until each one arrives.
 * The target text view is held weakly: a click may leave the screen while a request is in flight.
 */
class DrawableGetter(
    textView: TextView,
    private val maxWidth: Int,
    private val palette: MarkdownPalette,
) : ImageGetter, Drawable.Callback {

    private val target = WeakReference(textView)

    override fun getDrawable(url: String): Drawable {
        val slot = UrlDrawable()
        val textView = target.get() ?: return slot
        slot.setPlaceholder(ImageSlotDrawable.loading(textView.context, palette, maxWidth))
        slot.callback = this
        val request = ImageRequest.Builder(textView.context)
            .data(url)
            // Animated GIFs cannot be drawn on a hardware canvas.
            .allowHardware(false)
            .target(
                onSuccess = { result -> applyImage(slot, textView, result) },
                onError = { applyFailure(slot, textView) },
            )
            .build()
        textView.context.imageLoader.enqueue(request)
        return slot
    }

    private fun applyImage(slot: UrlDrawable, textView: TextView, picture: Drawable) {
        textView.post {
            val intrinsicWidth = picture.intrinsicWidth.coerceAtLeast(1)
            val intrinsicHeight = picture.intrinsicHeight.coerceAtLeast(1)
            val scale = if (intrinsicWidth > maxWidth) intrinsicWidth.toFloat() / maxWidth else 1f
            // 1.3: generous enough that wide screenshots do not dominate the text.
            val rect = Rect(
                0,
                0,
                (intrinsicWidth / scale / 1.3f).roundToInt(),
                (intrinsicHeight / scale / 1.3f).roundToInt(),
            )
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
            slot.setPlaceholder(ImageSlotDrawable.failed(textView.context, palette, maxWidth))
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
