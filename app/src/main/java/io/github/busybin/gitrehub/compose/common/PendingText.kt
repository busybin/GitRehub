package io.github.busybin.gitrehub.compose.common

/**
 * Text handed from an external share (or another screen) to the next editor.
 *
 * Intent extras and navigation routes are a poor fit for a whole issue body or gist file (size,
 * encoding), so this stays in memory for the one hop. A process death in between loses it, which
 * is acceptable: the share sheet would have to be used again.
 */
object PendingText {
    var shared: String? = null
    var issueBody: String? = null
    var gistContent: String? = null
}
