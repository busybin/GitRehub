package io.github.busybin.gitrehub.provider.markdown.extension.mention.internal

import io.github.busybin.gitrehub.provider.markdown.extension.mention.Mention
import org.commonmark.node.Node
import org.commonmark.node.Nodes
import org.commonmark.parser.delimiter.DelimiterProcessor
import org.commonmark.parser.delimiter.DelimiterRun

/** Takes `@` up to the next space and wraps it in a [Mention] node. */
class MentionDelimiterProcessor : DelimiterProcessor {
    override fun getOpeningCharacter() = '@'
    override fun getClosingCharacter() = ' '
    override fun getMinLength() = 1

    override fun process(openingRun: DelimiterRun, closingRun: DelimiterRun): Int {
        if (openingRun.length() < 1 || closingRun.length() < 1) return 0
        val node: Node = Mention()
        for (child in Nodes.between(openingRun.opener, closingRun.closer)) {
            node.appendChild(child)
        }
        openingRun.opener.insertAfter(node)
        return 1
    }
}
