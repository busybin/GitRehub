package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.SpannableStringBuilder
import io.github.busybin.gitrehub.provider.emoji.EmojiStore
import net.nightwhistler.htmlspanner.TagNodeHandler
import org.htmlcleaner.TagNode

/** Replaces `<emoji alias="rocket">` (produced by the markdown emoji extension) with its glyph. */
class EmojiHandler : TagNodeHandler() {
    override fun handleTagNode(
        node: TagNode,
        builder: SpannableStringBuilder,
        start: Int,
        end: Int
    ) {
        val alias = node.getAttributeByName("alias") ?: node.text?.toString()
        val glyph = EmojiStore.unicode(alias) ?: return
        builder.replace(start, end, " $glyph ")
    }
}
