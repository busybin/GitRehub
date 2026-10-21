package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

/**
 * Makes a body image tappable. [android.text.style.ImageSpan] is not a [ClickableSpan], so
 * `BetterLinkMovementExtended` never sees it; this span covers the same range and hands the click
 * to the caller (the modern UI opens a zoomable viewer).
 */
class ImageClickSpan(
    val url: String,
    private val onImageClick: (String) -> Unit,
) : ClickableSpan() {

    override fun updateDrawState(ds: TextPaint) {
        // No text inside the image range: no tint, no underline.
        ds.isUnderlineText = false
    }

    override fun onClick(widget: View) {
        onImageClick(url)
    }
}
