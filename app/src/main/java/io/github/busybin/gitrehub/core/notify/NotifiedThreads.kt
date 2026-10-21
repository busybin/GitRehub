package io.github.busybin.gitrehub.core.notify

import io.github.busybin.gitrehub.helper.PrefHelper

/**
 * Threads the background poller has already raised a notification for, so the next run does not
 * show the same one again. Ids are plain numbers in MMKV; the list is capped so it cannot grow
 * without bound.
 */
object NotifiedThreads {

    private const val KEY = "notified_threads"
    private const val LIMIT = 2000

    fun ids(): Set<Long> = PrefHelper.getStringSet(KEY).mapNotNullTo(HashSet()) { it.toLongOrNull() }

    /** Adds [ids] to the shown set, dropping the oldest entries beyond [LIMIT]. */
    fun remember(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val merged = LinkedHashSet(PrefHelper.getStringSet(KEY))
        ids.forEach { merged.add(it.toString()) }
        val kept = if (merged.size > LIMIT) merged.toList().takeLast(LIMIT) else merged.toList()
        PrefHelper.putStringSet(KEY, kept.toSet())
    }

    fun clear() = PrefHelper.putStringSet(KEY, emptySet())
}
