package io.github.busybin.gitrehub.provider.timeline.handler

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import net.nightwhistler.htmlspanner.TagNodeHandler
import org.htmlcleaner.TagNode

/**
 * Renders an HTML table as one text block: cells joined with ` | `, header cells bold.
 *
 * Images are clamped to the column width with [CappedImageSpan]; when a column would be narrower
 * than [minColumnWidth] the row stacks one cell per line instead (screenshot grids).
 */
class TableHandler : TagNodeHandler() {
    private var tableWidth = 500
    private var minColumnWidth = MIN_IMAGE_WIDTH

    override fun rendersContent() = true

    override fun handleTagNode(node: TagNode, builder: SpannableStringBuilder, start: Int, end: Int) {
        for (row in rowsOf(node)) {
            appendRow(builder, row)
        }
        builder.append("\n")
    }

    fun setTableWidth(width: Int) {
        tableWidth = width
    }

    fun setMinColumnWidth(width: Int) {
        minColumnWidth = width.coerceAtLeast(MIN_IMAGE_WIDTH)
    }

    /** Kept for callers that used the drawn-table version; the inline renderer ignores it. */
    fun setTextColor(color: Int) = Unit

    private fun rowsOf(table: TagNode): List<Row> {
        val rows = mutableListOf<Row>()
        fun walk(node: TagNode) {
            if (node.name == "tr") {
                val cells = node.childTags.filter { it.name == "td" || it.name == "th" }
                if (cells.isNotEmpty()) {
                    rows += Row(cells.map { spanner.fromTagNode(it) }, header = cells.all { it.name == "th" })
                }
                return
            }
            node.childTags.forEach(::walk)
        }
        walk(table)
        return rows
    }

    private fun appendRow(builder: SpannableStringBuilder, row: Row) {
        val columns = row.cells.size.coerceAtLeast(1)
        val hasImage = row.cells.any { cell -> cell.getSpans(0, cell.length, ImageSpan::class.java).isNotEmpty() }
        val stacked = hasImage && tableWidth / columns < minColumnWidth
        val maxImageWidth = (if (stacked) tableWidth else tableWidth / columns) - 2 * PADDING
        row.cells.forEachIndexed { index, cell ->
            if (index > 0) builder.append(if (stacked) "\n" else " | ")
            appendCell(builder, cell, maxImageWidth.coerceAtLeast(MIN_IMAGE_WIDTH), row.header)
            while (builder.isNotEmpty() && builder[builder.length - 1] == '\n') {
                builder.delete(builder.length - 1, builder.length)
            }
        }
        builder.append("\n")
    }

    private fun appendCell(builder: SpannableStringBuilder, cell: Spanned, maxImageWidth: Int, header: Boolean) {
        val cellStart = builder.length
        builder.append(cell)
        val cellEnd = builder.length
        builder.getSpans(cellStart, cellEnd, ImageSpan::class.java).forEach { span ->
            val from = builder.getSpanStart(span)
            val to = builder.getSpanEnd(span)
            val flags = builder.getSpanFlags(span)
            builder.removeSpan(span)
            builder.setSpan(CappedImageSpan(span.drawable, maxImageWidth), from, to, flags)
        }
        if (header && cellEnd > cellStart) {
            builder.setSpan(StyleSpan(Typeface.BOLD), cellStart, cellEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private class Row(val cells: List<Spanned>, val header: Boolean)

    private companion object {
        const val PADDING = 20
        const val MIN_IMAGE_WIDTH = 48
    }
}
