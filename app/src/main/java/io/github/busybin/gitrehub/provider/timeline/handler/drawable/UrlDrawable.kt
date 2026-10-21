package io.github.busybin.gitrehub.provider.timeline.handler.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable

/** Slot for an asynchronously loaded body image: it shows a placeholder until Coil answers. */
internal class UrlDrawable : Drawable(), Drawable.Callback {
    var image: Drawable? = null
        private set
    private var placeholder: Drawable? = null

    override fun draw(canvas: Canvas) {
        val picture = image
        if (picture == null) {
            placeholder?.let {
                it.setBounds(bounds)
                it.draw(canvas)
            }
            return
        }
        // Table cells can narrow the span after the picture was sized; follow the layout bounds.
        if (bounds.width() > 0 && bounds.height() > 0 && picture.bounds != bounds) {
            picture.bounds = bounds
        }
        picture.draw(canvas)
        if (picture is Animatable && !picture.isRunning) picture.start()
    }

    fun setImage(drawable: Drawable?) {
        image?.callback = null
        image = drawable?.also { it.callback = this }
    }

    fun setPlaceholder(drawable: Drawable?) {
        if (placeholder === drawable) return
        placeholder?.callback = null
        placeholder = drawable?.also { it.callback = this }
        invalidateSelf()
    }

    override fun invalidateDrawable(who: Drawable) {
        callback?.invalidateDrawable(who)
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) {
        callback?.scheduleDrawable(who, what, whenMillis)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        callback?.unscheduleDrawable(who, what)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
