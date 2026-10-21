package io.github.busybin.gitrehub.provider.markdown.extension.emoji.internal

import io.github.busybin.gitrehub.provider.markdown.extension.emoji.Emoji
import org.commonmark.node.Node
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext

class EmojiNodeRenderer(private val context: HtmlNodeRendererContext) : NodeRenderer {
    override fun getNodeTypes(): Set<Class<out Node>> = setOf(Emoji::class.java)

    override fun render(node: Node) {
        val html = context.writer
        html.tag("emoji", context.extendAttributes(node, "emoji", emptyMap()))
        var child = node.firstChild
        while (child != null) {
            val next = child.next
            context.render(child)
            child = next
        }
        html.tag("/emoji")
    }
}
