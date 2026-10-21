package io.github.busybin.gitrehub.provider.markdown.extension.mention.internal

import io.github.busybin.gitrehub.provider.markdown.extension.mention.Mention
import org.commonmark.node.Node
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext

class MentionNodeRenderer(private val context: HtmlNodeRendererContext) : NodeRenderer {
    override fun getNodeTypes(): Set<Class<out Node>> = setOf(Mention::class.java)

    override fun render(node: Node) {
        val html = context.writer
        html.tag("mention", context.extendAttributes(node, "mention", emptyMap()))
        var child = node.firstChild
        while (child != null) {
            val next = child.next
            context.render(child)
            child = next
        }
        html.tag("/mention")
    }
}
