package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.widget.TextView
import io.github.busybin.gitrehub.helper.PrefGetter.isAutoImageDisabled
import io.github.busybin.gitrehub.helper.WebLinks
import io.github.busybin.gitrehub.helper.rendering.SpannableBuilder.Companion.builder
import io.github.busybin.gitrehub.provider.scheme.SchemeParser
import io.github.busybin.gitrehub.provider.timeline.MarkdownPalette
import io.github.busybin.gitrehub.provider.timeline.handler.drawable.DrawableGetter
import net.nightwhistler.htmlspanner.TagNodeHandler
import org.htmlcleaner.ContentNode
import org.htmlcleaner.TagNode

/** `<img>`: a Coil-backed image span, or a tappable "Image" label when auto-loading is off. */
class DrawableHandler(
    private val textView: TextView?,
    private val width: Int,
    private val palette: MarkdownPalette,
    private val baseUrl: String? = null,
    private val onImageClick: ((String) -> Unit)? = null,
) : TagNodeHandler() {

    override fun handleTagNode(node: TagNode, builder: SpannableStringBuilder, start: Int, end: Int) {
        val src = WebLinks.resolve(baseUrl, node.getAttributeByName("src"))
            ?.takeIf { it.isNotBlank() } ?: return
        // Badge rows (READMEs are full of `<p align="center"><a><img></a>…`) are inline: no line
        // break after each one, and let the text layout wrap them. A lone image stays a block.
        val inline = isInline(node)
        val alt = node.getAttributeByName("alt")?.trim().orEmpty()
        if (isAutoImageDisabled) {
            builder.append(builder().clickable("Image") { view: View ->
                SchemeParser.launchUri(view.context, src)
            })
            builder.append(if (inline) " " else "\n")
            return
        }
        if (textView == null) {
            builder.append(if (inline) " " else "\n")
            return
        }
        val spanStart = builder.length
        builder.append("\uFFFC")
        val image = DrawableGetter(
            textView,
            width,
            palette,
            vector = isVector(node, src),
            inline = inline,
            alt = alt.ifBlank { null },
        ).getDrawable(src)
        builder.setSpan(ImageSpan(image), spanStart, spanStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (onImageClick != null && !linksElsewhere(node)) {
            builder.setSpan(
                ImageClickSpan(src, onImageClick),
                spanStart,
                spanStart + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        // Source whitespace between badges is stripped by HtmlSpanner, so re-add a separator to
        // give the layout a break opportunity (otherwise a long row is clipped instead of wrapped).
        builder.append(if (inline) " " else "\n")
    }

    /** True when the image sits in a paragraph that also holds other images or text. */
    private fun isInline(node: TagNode): Boolean {
        var parent: TagNode? = node.parent
        while (parent != null && parent.name.equals("a", ignoreCase = true)) parent = parent.parent
        if (parent == null) return false
        if (!parent.name.equals("p", true) && !parent.name.equals("div", true)) return false
        var images = 0
        var text = false
        fun scan(tag: TagNode) {
            for (child in tag.children) {
                when (child) {
                    is TagNode ->
                        if (child.name.equals("img", true)) images++ else scan(child)
                    is ContentNode ->
                        if (child.content.toString().isNotBlank()) text = true
                    else -> Unit
                }
            }
        }
        scan(parent)
        return images > 1 || text
    }

    /** shields.io badges reach us through camo, which keeps the original URL in a data attribute. */
    private fun isVector(node: TagNode, src: String): Boolean =
        sequenceOf(node.getAttributeByName("data-canonical-src"), src)
            .filterNotNull()
            .any { it.substringBefore('#').substringBefore('?').endsWith(".svg", ignoreCase = true) }

    /** True when the image is wrapped in a link to a different target (a badge): the link wins. */
    private fun linksElsewhere(node: TagNode): Boolean {
        val parent = node.parent ?: return false
        if (!parent.name.equals("a", ignoreCase = true)) return false
        val href = parent.getAttributeByName("href") ?: return false
        return fileName(href) != fileName(node.getAttributeByName("src").orEmpty())
    }

    private fun fileName(url: String): String =
        url.substringAfterLast('/').substringBefore('#').substringBefore('?')
}
