package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.SpannableStringBuilder
import androidx.annotation.ColorInt
import net.nightwhistler.htmlspanner.TagNodeHandler
import org.htmlcleaner.TagNode

class QuoteHandler(@field:ColorInt private val color: Int) : TagNodeHandler() {
    override fun handleTagNode(node: TagNode, builder: SpannableStringBuilder, start: Int, end: Int) {
        val from = if (start + 1 <= builder.length) start + 1 else start
        builder.setSpan(MarkdownQuoteSpan(color), from, builder.length, 33)
    }
}
