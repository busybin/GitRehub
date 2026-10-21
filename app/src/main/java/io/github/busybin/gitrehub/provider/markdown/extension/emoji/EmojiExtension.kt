package io.github.busybin.gitrehub.provider.markdown.extension.emoji

import io.github.busybin.gitrehub.provider.markdown.extension.emoji.internal.EmojiDelimiterProcessor
import io.github.busybin.gitrehub.provider.markdown.extension.emoji.internal.EmojiNodeRenderer
import org.commonmark.Extension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/** Parses `:alias:` and renders it as `<emoji alias="…">` for [io.github.busybin.gitrehub.provider.timeline.handler.EmojiHandler]. */
class EmojiExtension private constructor() :
    Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customDelimiterProcessor(EmojiDelimiterProcessor())
    }

    override fun extend(rendererBuilder: HtmlRenderer.Builder) {
        rendererBuilder.nodeRendererFactory { EmojiNodeRenderer(it) }
    }

    companion object {
        fun create(): Extension = EmojiExtension()
    }
}
