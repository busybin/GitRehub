package io.github.busybin.gitrehub.provider.markdown.extension.emoji.internal

import io.github.busybin.gitrehub.provider.markdown.extension.emoji.Emoji
import org.commonmark.node.Node
import org.commonmark.node.Nodes
import org.commonmark.parser.delimiter.DelimiterProcessor
import org.commonmark.parser.delimiter.DelimiterRun

/** Pairs the two `:` of an alias and moves the text between them into an [Emoji] node. */
class EmojiDelimiterProcessor : DelimiterProcessor {
    override fun getOpeningCharacter() = ':'
    override fun getClosingCharacter() = ':'
    override fun getMinLength() = 1

    override fun process(openerRun: DelimiterRun, closerRun: DelimiterRun): Int {
        if (openerRun.length() < 1 || closerRun.length() < 1) return 0
        val node: Node = Emoji()
        for (child in Nodes.between(openerRun.opener, closerRun.closer)) {
            node.appendChild(child)
        }
        openerRun.opener.insertAfter(node)
        return 1
    }
}
