package io.github.busybin.gitrehub.provider.markdown.extension.mention

import org.commonmark.node.CustomNode
import org.commonmark.node.Delimited

/** `@login` node; the HTML handler makes it a profile link. */
class Mention : CustomNode(), Delimited {
    override fun getOpeningDelimiter() = "@"
    override fun getClosingDelimiter() = " "
}
