package io.github.busybin.gitrehub.compose.common

import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "What have I already read" — shared by the activity feed and the notifications screen.
 *
 * The official client's filtering is one of the things users complain about most ("no advanced
 * filtering", "does not hide what you already read"), and the two screens see the same activity from
 * two angles: the notifications list is GitHub's notification threads, the feed is repository
 * activity. Reading a thread in one of them has to show up in the other, so instead of each screen
 * keeping its own bookkeeping they both write here:
 *
 * - [readIds]  — activity *event* ids (rows the feed itself opened; events such as pushes or stars
 *   have no thread to match on),
 * - [readKeys] — [ThreadKey]s (`owner/repo#i42`), which notifications and thread-based feed events
 *   can both be resolved to.
 *
 * Keys are `m3_` prefixed and stored through [PrefHelper]. Both sets are bounded.
 */
object ReadState {

    private const val KEY_READ_IDS = "m3_feed_read_ids"
    private const val KEY_READ_KEYS = "m3_read_thread_keys"
    private const val KEY_READ_KEY_TIMES = "m3_read_thread_times"

    private const val MAX_READ_IDS = 500
    private const val MAX_READ_KEYS = 500

    /** Insertion-ordered so the oldest ids can be dropped when the list grows. */
    private val readStore: LinkedHashSet<Long> = LinkedHashSet(readStoredIds())

    private val _readIds = MutableStateFlow<Set<Long>>(readStore.toSet())
    val readIds: StateFlow<Set<Long>> = _readIds.asStateFlow()

    /** Insertion-ordered, like [readStore]. */
    private val readKeyStore: LinkedHashSet<String> = LinkedHashSet(readStoredKeys())

    private val _readKeys = MutableStateFlow<Set<String>>(readKeyStore.toSet())
    val readKeys: StateFlow<Set<String>> = _readKeys.asStateFlow()

    /**
     * When each [readKeyStore] entry was marked, so a notification that got new activity afterwards
     * becomes unread again instead of staying read forever (see [ThreadKey.isReadLocally]).
     */
    private val readTimeStore: LinkedHashMap<String, Long> = LinkedHashMap(
        readStoredTimes().filterKeys { it in readKeyStore },
    )

    private val _readKeyTimes = MutableStateFlow<Map<String, Long>>(readTimeStore.toMap())
    val readKeyTimes: StateFlow<Map<String, Long>> = _readKeyTimes.asStateFlow()

    fun markRead(id: Long) {
        if (id == 0L) return
        if (readStore.add(id)) {
            trimIds()
            persistIds()
        }
    }

    fun markAllRead(ids: Collection<Long>) {
        var changed = false
        ids.forEach { id ->
            if (id != 0L && readStore.add(id)) changed = true
        }
        if (changed) {
            trimIds()
            persistIds()
        }
    }

    /** Marks a single notification/feed thread (see [ThreadKey]). */
    fun markReadKey(key: String?, at: Long = System.currentTimeMillis()) {
        if (key.isNullOrBlank()) return
        if (readKeyStore.add(key)) {
            trimKeys()
            persistKeys()
        }
        val previous = readTimeStore[key]
        if (previous == null || at > previous) {
            readTimeStore[key] = at
            persistTimes()
        }
    }

    fun markReadKeys(keys: Collection<String>, at: Long = System.currentTimeMillis()) {
        var changed = false
        keys.forEach { key ->
            if (key.isBlank()) return@forEach
            if (readKeyStore.add(key)) changed = true
            val previous = readTimeStore[key]
            if (previous == null || at > previous) {
                readTimeStore[key] = at
                changed = true
            }
        }
        if (changed) {
            trimKeys()
            persistKeys()
            persistTimes()
        }
    }

    /** Undo [markReadKey] — used when a notification is swiped back to unread. */
    fun unmarkReadKey(key: String?) {
        if (key.isNullOrBlank()) return
        val removedKey = readKeyStore.remove(key)
        val removedTime = readTimeStore.remove(key) != null
        if (removedKey) persistKeys()
        if (removedTime) persistTimes()
    }

    /** True when this event was read (either the event itself or its underlying thread). */
    fun isRead(eventId: Long, threadKey: String?): Boolean =
        readStore.contains(eventId) || (threadKey != null && readKeyStore.contains(threadKey))

    fun clear() {
        readStore.clear()
        readKeyStore.clear()
        readTimeStore.clear()
        PrefHelper.clearKey<String>(KEY_READ_IDS)
        PrefHelper.clearKey<String>(KEY_READ_KEYS)
        PrefHelper.clearKey<String>(KEY_READ_KEY_TIMES)
        _readIds.value = emptySet()
        _readKeys.value = emptySet()
        _readKeyTimes.value = emptyMap()
    }

    private fun trimIds() {
        while (readStore.size > MAX_READ_IDS) {
            val oldest = readStore.firstOrNull() ?: return
            readStore.remove(oldest)
        }
    }

    private fun trimKeys() {
        while (readKeyStore.size > MAX_READ_KEYS) {
            val oldest = readKeyStore.firstOrNull() ?: return
            readKeyStore.remove(oldest)
            readTimeStore.remove(oldest)
        }
    }

    private fun persistIds() {
        // An empty set cannot be stored via putAny(""): it treats an empty string as "do not store",
        // so once everything is deleted the old value would stay on disk and reappear on next start
        // (same trap as LanguagePrefs).
        if (readStore.isEmpty()) {
            PrefHelper.clearKey<String>(KEY_READ_IDS)
        } else {
            PrefHelper.putAny(KEY_READ_IDS, readStore.joinToString(","))
        }
        _readIds.value = readStore.toSet()
    }

    private fun persistKeys() {
        if (readKeyStore.isEmpty()) {
            PrefHelper.clearKey<String>(KEY_READ_KEYS)
        } else {
            PrefHelper.putAny(KEY_READ_KEYS, readKeyStore.joinToString(","))
        }
        _readKeys.value = readKeyStore.toSet()
    }

    private fun persistTimes() {
        if (readTimeStore.isEmpty()) {
            PrefHelper.clearKey<String>(KEY_READ_KEY_TIMES)
        } else {
            PrefHelper.putAny(
                KEY_READ_KEY_TIMES,
                readTimeStore.entries.joinToString(",") { "${it.key}@${it.value}" },
            )
        }
        _readKeyTimes.value = readTimeStore.toMap()
    }

    private fun readStoredIds(): List<Long> = PrefHelper.getString(KEY_READ_IDS)
        .orEmpty()
        .split(',')
        .mapNotNull { it.trim().toLongOrNull() }

    private fun readStoredKeys(): List<String> = PrefHelper.getString(KEY_READ_KEYS)
        .orEmpty()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    /** `owner/repo#i42@1699999999999`; repo names never contain `@`, so the last one separates the time. */
    private fun readStoredTimes(): Map<String, Long> = PrefHelper.getString(KEY_READ_KEY_TIMES)
        .orEmpty()
        .split(',')
        .mapNotNull { entry ->
            val at = entry.lastIndexOf('@')
            if (at <= 0) return@mapNotNull null
            val key = entry.substring(0, at).trim()
            val time = entry.substring(at + 1).trim().toLongOrNull() ?: return@mapNotNull null
            key.takeIf { it.isNotEmpty() }?.let { it to time }
        }
        .toMap()
}
