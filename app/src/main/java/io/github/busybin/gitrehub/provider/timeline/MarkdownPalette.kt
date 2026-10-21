package io.github.busybin.gitrehub.provider.timeline

import androidx.annotation.ColorInt

/**
 * Read-view colors, supplied by the Compose layer from the current theme (see
 * `compose/common/MarkdownText.kt`); the legacy pipeline used its own hardcoded colors instead.
 */
data class MarkdownPalette(
    @ColorInt val text: Int,
    @ColorInt val link: Int,
    @ColorInt val inlineCodeBackground: Int,
    @ColorInt val inlineCodeText: Int,
    @ColorInt val codeBackground: Int,
    @ColorInt val codeText: Int,
    @ColorInt val quote: Int,
    @ColorInt val divider: Int,
    val codeColors: MarkdownCodeColors = MarkdownCodeColors.None,
)

/** Token colors for fenced code blocks; [None] means no highlighting. */
data class MarkdownCodeColors(
    @ColorInt val keyword: Int,
    @ColorInt val comment: Int,
    @ColorInt val string: Int,
    @ColorInt val number: Int,
    @ColorInt val function: Int,
    @ColorInt val type: Int,
) {
    companion object {
        val None = MarkdownCodeColors(0, 0, 0, 0, 0, 0)
    }
}

/** 0 is the "not set" sentinel (real colors always carry alpha). */
val MarkdownCodeColors?.isSet: Boolean get() = this != null && this.keyword != 0

