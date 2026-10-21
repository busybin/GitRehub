package io.github.busybin.gitrehub.provider.markdown

import android.text.Html
import android.widget.TextView
import io.github.busybin.gitrehub.helper.InputHelper.isEmpty
import io.github.busybin.gitrehub.provider.markdown.extension.emoji.EmojiExtension
import io.github.busybin.gitrehub.provider.markdown.extension.mention.MentionExtension
import io.github.busybin.gitrehub.provider.timeline.HtmlHelper
import io.github.busybin.gitrehub.provider.timeline.MarkdownPalette
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.ins.InsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Markdown -> HTML -> Spannable. GitHub extensions (tables, strikethrough, autolinks, emoji,
 * mentions) are handled by commonmark plus our two custom nodes; the HTML stage is [HtmlHelper].
 */
object MarkDownProvider {
    private val extensions: List<Extension> = listOf(
        StrikethroughExtension.create(),
        AutolinkExtension.create(),
        TablesExtension.create(),
        InsExtension.create(),
        EmojiExtension.create(),
        MentionExtension.create(),
        YamlFrontMatterExtension.create(),
    )

    private val parser by lazy { Parser.builder().extensions(extensions).build() }
    private val renderer by lazy { HtmlRenderer.builder().extensions(extensions).build() }

    @JvmStatic
    fun setMdText(
        textView: TextView,
        markdown: String?,
        width: Int,
        palette: MarkdownPalette,
        baseUrl: String? = null,
        linkBaseUrl: String? = null,
        onImageClick: ((String) -> Unit)? = null,
        onAnchorClick: ((String) -> Unit)? = null,
    ) {
        if (isEmpty(markdown)) return
        // A broken document should still show something: fall back to the raw text.
        val html = runCatching { markdownToHtml(markdown!!) }.getOrDefault(markdown!!)
        HtmlHelper.htmlIntoTextView(
            textView,
            html,
            width - textView.paddingStart - textView.paddingEnd,
            palette,
            baseUrl,
            linkBaseUrl,
            onImageClick,
            onAnchorClick,
        )
    }

    @JvmStatic
    fun stripMdText(markdown: String?): String {
        if (isEmpty(markdown)) return ""
        return Html.fromHtml(markdownToHtml(markdown!!), Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun markdownToHtml(markdown: String): String =
        renderer.render(parser.parse(markdown))
}
