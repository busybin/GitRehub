package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import androidx.annotation.ColorInt
import io.github.busybin.gitrehub.provider.syntax.Syntax
import io.github.busybin.gitrehub.provider.syntax.TokenKind
import io.github.busybin.gitrehub.provider.timeline.MarkdownCodeColors
import io.github.busybin.gitrehub.provider.timeline.isSet
import net.nightwhistler.htmlspanner.handlers.PreHandler
import org.htmlcleaner.TagNode

/** `<pre>` (fenced) and `<code>` (inline), with token colours when the language is known. */
class PreTagHandler(
    @field:ColorInt private val background: Int,
    private val block: Boolean,
    @field:ColorInt private val textColor: Int,
    private val codeColors: MarkdownCodeColors? = null,
) : PreHandler() {

    override fun handleTagNode(node: TagNode, builder: SpannableStringBuilder, start: Int, end: Int) {
        if (block) renderBlock(node, builder, start) else renderInline(node, builder, start)
    }

    private fun renderBlock(node: TagNode, builder: SpannableStringBuilder, start: Int) {
        val code = unescape(node.text?.toString().orEmpty()).trim('\n')
        val from = start + 1
        builder.append('\n').append(code).append("\n\n")
        val to = builder.length
        builder.setSpan(CodeBackgroundRoundedSpan(background), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(ForegroundColorSpan(textColor), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        colourTokens(builder, code, node, from)
        builder.append('\n')
        appendNewLine(builder)
    }

    private fun renderInline(node: TagNode, builder: SpannableStringBuilder, start: Int) {
        val code = unescape(node.text?.toString().orEmpty())
        builder.append(' ').append(code).append(' ')
        val from = start + 1
        val to = builder.length - 1
        builder.setSpan(BackgroundColorSpan(background), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(ForegroundColorSpan(textColor), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(TypefaceSpan("monospace"), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun colourTokens(builder: SpannableStringBuilder, code: String, node: TagNode, offset: Int) {
        val palette = codeColors?.takeIf { it.isSet } ?: return
        val language = languageOf(node) ?: return
        val tokens = Syntax.tokenize(code, language)
        tokens.forEach { token ->
            val color = when (token.kind) {
                TokenKind.COMMENT -> palette.comment
                TokenKind.KEYWORD -> palette.keyword
                TokenKind.STRING -> palette.string
                TokenKind.NUMBER -> palette.number
                TokenKind.FUNCTION, TokenKind.ANNOTATION -> palette.function
                TokenKind.TYPE -> palette.type
            }
            val from = offset + token.start
            val to = offset + token.end
            if (from >= 0 && to <= builder.length && to > from) {
                builder.setSpan(ForegroundColorSpan(color), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    /** Looks at the node, its children and up to three ancestors for `language-x`/`lang-x`. */
    private fun languageOf(node: TagNode): String? {
        var current: TagNode? = node
        var depth = 0
        while (current != null && depth < 4) {
            languageFrom(current)?.let { return it }
            current.childTags?.forEach { child -> languageFrom(child)?.let { return it } }
            current = current.parent
            depth++
        }
        return null
    }

    private fun languageFrom(node: TagNode): String? =
        node.getAttributeByName("class")?.let { CLASS.find(it) }?.groupValues?.get(1)
            ?.let(Syntax::normalizeLanguage)

    private fun unescape(text: String): String {
        var value = text
        HTML_ESCAPES.forEach { (escaped, plain) -> value = value.replace(escaped, plain) }
        return value.replace('\u00A0', '\u00A0')
    }

    private companion object {
        val CLASS = Regex("""(?:language|lang|highlight-source)-([A-Za-z0-9+#]+)""")
        val HTML_ESCAPES = mapOf(
            "&nbsp;" to "\u00A0", "&amp;" to "&", "&quot;" to "\"", "&lt;" to "<", "&gt;" to ">",
            "&cent;" to "¢", "&sect;" to "§", "&ldquo;" to "“", "&rdquo;" to "”",
            "&lsquo;" to "‘", "&rsquo;" to "’", "&ndash;" to "–", "&mdash;" to "—",
            "&horbar;" to "―",
        )
    }
}
