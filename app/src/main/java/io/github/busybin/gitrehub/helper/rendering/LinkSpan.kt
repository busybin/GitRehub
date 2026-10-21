package io.github.busybin.gitrehub.helper.rendering

import android.text.TextPaint
import android.text.style.URLSpan

/** [URLSpan] without the underline, tinted with the palette's link colour. */
class LinkSpan(url: String, private val color: Int) : URLSpan(url) {
    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.color = color
        ds.isUnderlineText = false
    }
}
