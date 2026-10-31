package io.github.busybin.gitrehub.compose.common

import io.github.busybin.gitrehub.helper.PrefHelper

/**
 * Last time the user had a thread open, so the next visit can mark the comments that arrived since.
 *
 * Local-only (GitHub has no "last read" on issues). Written on leaving the screen, read on entry:
 * marking on entry would erase the marker before the user sees it.
 */
object ThreadSeen {

    private const val PREFIX = "m3_thread_seen_"

    fun lastSeen(key: String?): Long {
        if (key.isNullOrBlank()) return 0L
        return PrefHelper.getString(PREFIX + key)?.toLongOrNull() ?: 0L
    }

    fun markSeen(key: String?, at: Long = System.currentTimeMillis()) {
        if (key.isNullOrBlank()) return
        runCatching { PrefHelper.putAny(PREFIX + key, at.toString()) }
    }
}
