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
        if (isAutoImageDisabled) {
            builder.append(builder().clickable("Image") { view: View ->
                SchemeParser.launchUri(view.context, src)
            })
            builder.append("\n")
            return
        }
        if (textView == null) {
            builder.append("\n")
            return
        }
        val spanStart = builder.length
        builder.append("\uFFFC\n")
        val image = DrawableGetter(textView, width, palette).getDrawable(src)
        builder.setSpan(ImageSpan(image), spanStart, spanStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (onImageClick != null && !linksElsewhere(node)) {
            builder.setSpan(
                ImageClickSpan(src, onImageClick),
                spanStart,
                spanStart + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

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
