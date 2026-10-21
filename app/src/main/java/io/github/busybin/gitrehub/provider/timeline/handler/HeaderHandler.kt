package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import net.nightwhistler.htmlspanner.TagNodeHandler
import net.nightwhistler.htmlspanner.spans.FontFamilySpan
import org.htmlcleaner.TagNode

/** h1-h6: blank line, bold text scaled by [size], blank line. */
class HeaderHandler(private val size: Float) : TagNodeHandler() {
    override fun beforeChildren(node: TagNode?, builder: SpannableStringBuilder?) {
        appendNewLine(builder)
    }

    override fun handleTagNode(node: TagNode, builder: SpannableStringBuilder, start: Int, end: Int) {
        builder.setSpan(RelativeSizeSpan(size), start, end, FLAG)
        val existing = getFontFamilySpan(builder, start, end)
        builder.setSpan(
            FontFamilySpan(existing?.fontFamily ?: spanner.defaultFont).apply {
                isItalic = existing?.isItalic ?: false
                isBold = true
            },
            start,
            end,
            FLAG,
        )
        appendNewLine(builder)
    }

    private companion object {
        const val FLAG = 33
    }
}
