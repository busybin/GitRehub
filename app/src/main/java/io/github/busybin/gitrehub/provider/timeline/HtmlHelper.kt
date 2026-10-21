package io.github.busybin.gitrehub.provider.timeline

import android.annotation.SuppressLint
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.MenuItem
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.helper.AppHelper
import io.github.busybin.gitrehub.provider.scheme.SchemeParser.launchUri
import io.github.busybin.gitrehub.provider.timeline.handler.*
import net.nightwhistler.htmlspanner.HtmlSpanner
import net.nightwhistler.htmlspanner.handlers.BoldHandler

/**
 * Created by Kosh on 21 Apr 2017, 11:24 PM
 */
object HtmlHelper {
    /**
     * Feed HTML into a [TextView]. [palette] is required: all colors come from the current theme.
     * [baseUrl] is the prefix for relative image URLs and [linkBaseUrl] for relative link URLs
     * (they differ, see `compose/common/MarkdownText.kt`); a non-null [onImageClick] makes images
     * tappable.
     */
    @JvmStatic
    fun htmlIntoTextView(
        textView: TextView,
        html: String,
        width: Int,
        palette: MarkdownPalette,
        baseUrl: String? = null,
        linkBaseUrl: String? = null,
        onImageClick: ((String) -> Unit)? = null,
        /** In-page `#heading` anchor: scroll within the same container, not open it as a link. */
        onAnchorClick: ((String) -> Unit)? = null,
    ) {
        registerClickEvent(textView, onAnchorClick)
        textView.setTextColor(palette.text)
        textView.setLinkTextColor(palette.link)
        // Selection highlight: tint the link color instead of the system's bright blue.
        textView.highlightColor = ColorUtils.setAlphaComponent(palette.link, 0x44)
        val htmlSpanner =
            initHtml(textView, width, palette, baseUrl, linkBaseUrl, onImageClick)
                .fromHtml(format(html).toString())
        textView.text = htmlSpanner
    }

    @SuppressLint("NonConstantResourceId")
    private fun registerClickEvent(textView: TextView, onAnchorClick: ((String) -> Unit)? = null) {
        val betterLinkMovementMethod = BetterLinkMovementExtended.linkifyHtml(textView)
        betterLinkMovementMethod.setOnLinkClickListener { view: TextView, url: String ->
            // In-page anchor (README `#xxx`), not an external link: scroll within the screen.
            if (url.startsWith("#") && onAnchorClick != null) {
                onAnchorClick(url.removePrefix("#"))
                return@setOnLinkClickListener true
            }
            launchUri(view.context, Uri.parse(url))
            true
        }
        betterLinkMovementMethod.setOnLinkLongClickListener { view: TextView, url: String ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val menu = PopupMenu(view.context, view)
            menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                when (menuItem.itemId) {
                    R.id.copy -> {
                        AppHelper.copyToClipboard(view.context, url)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.open -> {
                        if (url.startsWith("#") && onAnchorClick != null) {
                            onAnchorClick(url.removePrefix("#"))
                        } else {
                            launchUri(view.context, Uri.parse(url))
                        }
                        return@setOnMenuItemClickListener true
                    }
                    R.id.open_new_window -> {
                        launchUri(view.context, Uri.parse(url), false, true)
                        return@setOnMenuItemClickListener true
                    }
                    else -> return@setOnMenuItemClickListener false
                }
            }
            menu.inflate(R.menu.link_popup_menu)
            menu.show()
            true
        }
    }

    private fun initHtml(
        textView: TextView,
        width: Int,
        palette: MarkdownPalette,
        baseUrl: String?,
        linkBaseUrl: String?,
        onImageClick: ((String) -> Unit)?,
    ): HtmlSpanner {
        val checked = ContextCompat.getDrawable(textView.context, R.drawable.ic_checkbox_small)
        val unchecked =
            ContextCompat.getDrawable(textView.context, R.drawable.ic_checkbox_empty_small)
        val mySpanner = HtmlSpanner()
        mySpanner.isStripExtraWhiteSpace = true
        mySpanner.registerHandler(
            "pre",
            PreTagHandler(palette.codeBackground, true, palette.codeText, palette.codeColors)
        )
        mySpanner.registerHandler(
            "code",
            PreTagHandler(palette.inlineCodeBackground, false, palette.inlineCodeText)
        )
        mySpanner.registerHandler(
            "img",
            DrawableHandler(textView, width, palette, baseUrl, onImageClick)
        )
        mySpanner.registerHandler("g-emoji", EmojiHandler())
        mySpanner.registerHandler("blockquote", QuoteHandler(palette.quote))
        mySpanner.registerHandler("b", BoldHandler())
        mySpanner.registerHandler("strong", BoldHandler())
        mySpanner.registerHandler("i", ItalicHandler())
        mySpanner.registerHandler("em", ItalicHandler())
        mySpanner.registerHandler("ul", MarginHandler())
        mySpanner.registerHandler("ol", MarginHandler())
        mySpanner.registerHandler("li", ListsHandler(checked, unchecked))
        mySpanner.registerHandler("u", UnderlineHandler())
        mySpanner.registerHandler("strike", StrikethroughHandler())
        mySpanner.registerHandler("ins", UnderlineHandler())
        mySpanner.registerHandler("del", StrikethroughHandler())
        mySpanner.registerHandler("sub", SubScriptHandler())
        mySpanner.registerHandler("sup", SuperScriptHandler())
        mySpanner.registerHandler("a", LinkHandler(palette.link, linkBaseUrl))
        mySpanner.registerHandler("hr", HrHandler(palette.divider, width, false))
        mySpanner.registerHandler("emoji", EmojiHandler())
        mySpanner.registerHandler("mention", LinkHandler(palette.link, linkBaseUrl))
        mySpanner.registerHandler("h1", HeaderHandler(1.5f))
        mySpanner.registerHandler("h2", HeaderHandler(1.4f))
        mySpanner.registerHandler("h3", HeaderHandler(1.3f))
        mySpanner.registerHandler("h4", HeaderHandler(1.2f))
        mySpanner.registerHandler("h5", HeaderHandler(1.1f))
        mySpanner.registerHandler("h6", HeaderHandler(1.0f))

        if (width > 0) {
            val tableHandler = TableHandler()
            tableHandler.setTextColor(palette.text)
            tableHandler.setTableWidth(width)
            // A column narrower than ~64dp is not readable, so image rows stack instead.
            tableHandler.setMinColumnWidth((64 * textView.resources.displayMetrics.density).toInt())
            mySpanner.registerHandler("table", tableHandler)
        }
        return mySpanner
    }

    /** Blocks GitHub hides behind "show more" in notification mails, plus paragraph breaks. */
    private val HIDDEN_BLOCKS = listOf(
        Regex("""<span class="email-hidden-toggle">.*?</span>""", RegexOption.DOT_MATCHES_ALL),
        Regex("""<div class="email-(?:quoted-reply|signature-reply)">.*?</div>""", RegexOption.DOT_MATCHES_ALL),
        Regex("""<div class="email-hidden-reply"[^>]*>.*?</div>""", RegexOption.DOT_MATCHES_ALL),
    )

    fun format(html: String?): CharSequence {
        val source = html ?: return ""
        if (source.isEmpty()) return ""
        var text = source
        HIDDEN_BLOCKS.forEach { text = it.replace(text, "") }
        text = text.replace("<p>", "<br>").replace("</p>", "<br>")
        while (text.startsWith("<br>")) text = text.removePrefix("<br>")
        while (text.endsWith("<br>")) text = text.removeSuffix("<br>")
        return text.trim()
    }
}
