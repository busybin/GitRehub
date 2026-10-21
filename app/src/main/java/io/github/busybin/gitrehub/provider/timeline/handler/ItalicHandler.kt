package io.github.busybin.gitrehub.provider.timeline.handler

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import io.github.busybin.gitrehub.helper.rendering.FontSpan
import net.nightwhistler.htmlspanner.TagNodeHandler
import org.htmlcleaner.TagNode

class ItalicHandler : TagNodeHandler() {
    override fun handleTagNode(node: TagNode, builder: SpannableStringBuilder, start: Int, end: Int) {
        builder.setSpan(FontSpan(1f, Typeface.ITALIC), start, builder.length, 33)
    }
}
