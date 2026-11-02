package io.github.busybin.gitrehub.compose.common

import io.github.busybin.gitrehub.helper.PrefHelper

/**
 * When the user last had a repository open, so the next visit can say what changed since.
 *
 * Local only — GitHub has no per-user "last visit" on a repository. The very first visit records a
 * baseline and shows nothing, otherwise the card would announce the entire history as "new".
 */
object RepoSeen {

    private const val PREFIX = "m3_repo_seen_"

    fun lastSeen(fullName: String): Long =
        PrefHelper.getString(PREFIX + fullName)?.toLongOrNull() ?: 0L

    fun markSeen(fullName: String, at: Long = System.currentTimeMillis()) {
        if (fullName.isBlank()) return
        runCatching { PrefHelper.putAny(PREFIX + fullName, at.toString()) }
    }
}
