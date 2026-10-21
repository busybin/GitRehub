package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.SpannableStringBuilder
import io.github.busybin.gitrehub.helper.rendering.LinkSpan
import androidx.annotation.ColorInt
import io.github.busybin.gitrehub.helper.WebLinks
import net.nightwhistler.htmlspanner.TagNodeHandler
import org.htmlcleaner.TagNode

/**
 * Created by Kosh on 10 May 2017, 8:46 PM
 */
class LinkHandler(
    @field:ColorInt private val linkColor: Int,
    /** Prefix for resolving relative hrefs in README bodies (`docs/x.md`, `LICENSE`, …). */
    private val linkBaseUrl: String? = null,
) : TagNodeHandler() {
    override fun handleTagNode(
        node: TagNode,
        spannableStringBuilder: SpannableStringBuilder,
        start: Int,
        end: Int
    ) {
        val href = node.getAttributeByName("href")
        if (href != null) {
            // GitHub's API leaves body hrefs relative (`docs/x.md`); without resolving them
            // SchemeParser cannot route the click.
            val url = WebLinks.resolve(linkBaseUrl, href) ?: href
            spannableStringBuilder.setSpan(LinkSpan(url, linkColor), start, end, 33)
        } else if (node.text != null) {
            spannableStringBuilder.setSpan(
                LinkSpan(
                    "https://github.com/" + node.text.toString(),
                    linkColor
                ), start, end, 33
            )
        }
    }
}
