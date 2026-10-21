package io.github.busybin.gitrehub.provider.markdown.extension.mention

import io.github.busybin.gitrehub.provider.markdown.extension.mention.internal.MentionDelimiterProcessor
import io.github.busybin.gitrehub.provider.markdown.extension.mention.internal.MentionNodeRenderer
import org.commonmark.Extension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/** Parses `@login` and renders it as `<mention>` for `MentionHandler`. */
class MentionExtension private constructor() :
    Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customDelimiterProcessor(MentionDelimiterProcessor())
    }

    override fun extend(rendererBuilder: HtmlRenderer.Builder) {
        rendererBuilder.nodeRendererFactory { MentionNodeRenderer(it) }
    }

    companion object {
        fun create(): Extension = MentionExtension()
    }
}
