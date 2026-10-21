package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.LeadingMarginSpan
import net.nightwhistler.htmlspanner.TagNodeHandler
import org.htmlcleaner.TagNode

/** Indents a block (used for blockquotes and nested lists). */
class MarginHandler : TagNodeHandler() {
    override fun beforeChildren(node: TagNode, builder: SpannableStringBuilder) {
        if (builder.isNotEmpty() && builder[builder.length - 1] != '\n') appendNewLine(builder)
    }

    override fun handleTagNode(node: TagNode, builder: SpannableStringBuilder, start: Int, end: Int) {
        builder.setSpan(LeadingMarginSpan.Standard(INDENT), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        appendNewLine(builder)
    }

    private companion object {
        const val INDENT = 30
    }
}
