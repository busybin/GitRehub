package io.github.busybin.gitrehub.provider.timeline

/**
 * A heading of a rendered document: [level] is 1-6, [offset] the character index in the text the
 * renderer built, which is what `Layout.getLineForOffset` turns into a scroll position.
 */
data class OutlineSection(val level: Int, val title: String, val offset: Int)
