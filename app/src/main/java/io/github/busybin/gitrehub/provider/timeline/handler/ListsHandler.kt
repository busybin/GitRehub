package io.github.busybin.gitrehub.provider.timeline.handler

import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import io.github.busybin.gitrehub.helper.rendering.SpannableBuilder.Companion.builder
import net.nightwhistler.htmlspanner.TagNodeHandler
import org.htmlcleaner.TagNode

/** `<li>` inside `<ul>`/`<ol>`, including GitHub task-list checkboxes. */
class ListsHandler(private val checked: Drawable?, private val unchecked: Drawable?) :
    TagNodeHandler() {

    override fun beforeChildren(node: TagNode, builder: SpannableStringBuilder) {
        val parent = node.parent?.name
        if (parent == "ol") {
            builder.append("${indexOf(node)}. ")
        } else if (parent == "ul") {
            val task = node.childTags?.firstOrNull { it.name == "input" }
            when {
                task == null -> builder.append("\u2022 ")
                checked == null || unchecked == null ->
                    builder.append(if (task.getAttributeByName("checked") != null) "\u2611" else "\u2610")
                else -> builder.append(
                    builder().append(if (task.getAttributeByName("checked") != null) checked else unchecked)
                ).append(" ")
            }
        }
    }

    override fun handleTagNode(node: TagNode, builder: SpannableStringBuilder, start: Int, end: Int) {
        appendNewLine(builder)
    }

    private fun indexOf(node: TagNode): Int {
        val siblings = node.parent?.children ?: return 1
        var index = 1
        for (sibling in siblings) {
            if (sibling === node) return index
            if (sibling is TagNode && sibling.name == "li") index++
        }
        return 1
    }
}
