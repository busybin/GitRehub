package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.style.AlignmentSpan
import android.text.style.RelativeSizeSpan
import io.github.busybin.gitrehub.provider.timeline.OutlineSection
import net.nightwhistler.htmlspanner.TagNodeHandler
import net.nightwhistler.htmlspanner.spans.FontFamilySpan
import org.htmlcleaner.TagNode

/**
 * h1-h6: blank line, bold text scaled by [size], blank line.
 *
 * HtmlSpanner's built-in heading handler honours `align="center"`; the custom one used here must
 * do it too, otherwise GitHub READMEs (`<h1 align="center">Title</h1>`) lose their centering.
 *
 * When [outline] is given, the heading is recorded there for the table of contents.
 */
class HeaderHandler(
    private val size: Float,
    private val level: Int = 1,
    private val outline: MutableList<OutlineSection>? = null,
) : TagNodeHandler() {
    override fun beforeChildren(node: TagNode?, builder: SpannableStringBuilder?) {
        appendNewLine(builder)
    }

    override fun handleTagNode(node: TagNode, builder: SpannableStringBuilder, start: Int, end: Int) {
        // Skip the newlines beforeChildren() added: they belong to the neighbouring lines, and an
        // AlignmentSpan that covers them would align those lines too.
        var from = start
        while (from < end && builder[from] == '\n') from++
        var to = end
        while (to > from && builder[to - 1] == '\n') to--
        val alignment = when (node.getAttributeByName("align")?.lowercase()) {
            "center" -> Layout.Alignment.ALIGN_CENTER
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> null
        }
        if (alignment != null && to > from) {
            builder.setSpan(AlignmentSpan.Standard(alignment), from, to, FLAG)
        }
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
        if (outline != null && to > from) {
            val title = builder.subSequence(from, to).toString().trim()
            if (title.isNotEmpty()) outline.add(OutlineSection(level, title, from))
        }
        appendNewLine(builder)
    }

    private companion object {
        const val FLAG = 33
    }
}
