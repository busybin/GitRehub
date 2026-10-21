package io.github.busybin.gitrehub.provider.markdown.extension.emoji

import org.commonmark.node.CustomNode
import org.commonmark.node.Delimited

/** `:alias:` node; the HTML handler turns the alias into a glyph. */
class Emoji : CustomNode(), Delimited {
    override fun getOpeningDelimiter() = ":"
    override fun getClosingDelimiter() = ":"
}
