package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.SpannableStringBuilder
import android.text.style.UnderlineSpan
import net.nightwhistler.htmlspanner.TagNodeHandler
import org.htmlcleaner.TagNode

class UnderlineHandler : TagNodeHandler() {
    override fun handleTagNode(node: TagNode, builder: SpannableStringBuilder, start: Int, end: Int) {
        builder.setSpan(UnderlineSpan(), start, end, 33)
    }
}
