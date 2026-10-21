package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.SpannableStringBuilder
import android.text.Spanned
import net.nightwhistler.htmlspanner.TagNodeHandler
import net.nightwhistler.htmlspanner.spans.CenterSpan
import org.htmlcleaner.TagNode

/** `<hr>`: a centred replacement span that paints a rule. */
class HrHandler(private val color: Int, private val width: Int, private val isHeader: Boolean) :
    TagNodeHandler() {
    override fun handleTagNode(node: TagNode, builder: SpannableStringBuilder, start: Int, end: Int) {
        builder.append("\n")
        val rule = SpannableStringBuilder("$")
        rule.setSpan(HrSpan(color, width), 0, rule.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        rule.setSpan(CenterSpan(), 0, rule.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        rule.append("\n")
        builder.append(rule)
    }
}
