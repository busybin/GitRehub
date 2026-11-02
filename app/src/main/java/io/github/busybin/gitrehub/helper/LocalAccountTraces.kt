package io.github.busybin.gitrehub.helper

/**
 * Local records that mention what the signed-in account looked at: read state, push watermarks and
 * unsent comment drafts. The keys contain `owner/repo`, and drafts carry the text itself.
 *
 * Read state and push watermarks are dropped when the device ends up with no account at all
 * (sign-out, or removing the last account). They are deliberately **not** dropped when merely
 * switching accounts: the read state is device-wide, so clearing it on a switch would turn
 * everything the other account already read back to unread. Drafts follow the stricter rule and
 * are dropped on every account change because they contain the user's own text; saved threads do
 * the same. In-memory copies register through [addClearHook], otherwise a cached list can write the
 * deleted records back on the next edit.
 */
object LocalAccountTraces {

    /** Per-repository/per-thread timestamps; the key itself contains `owner/repo`. */
    private val PREFIXES = listOf("m3_repo_seen_", "m3_thread_seen_")

    /**
     * Read state, push watermarks and unsent drafts. Their key names contain `owner/repo` and the
     * drafts even carry the text the user typed. All of them are stored as `String` values (see
     * `ReadState` and `DraftQueue`), so they must be removed with the `String` tag.
     */
    private val KEYS = listOf(
        "m3_feed_read_ids",
        "m3_read_thread_keys",
        "m3_read_thread_times",
        "m3_feed_read_pushes",
        "m3_comment_drafts",
    )

    /**
     * In-memory copies (read state, draft queue) register here: deleting the MMKV keys alone lets a
     * process-lifetime cache write the deleted records back on the next edit.
     */
    private val hooks = mutableListOf<() -> Unit>()

    fun addClearHook(hook: () -> Unit) {
        synchronized(hooks) { hooks += hook }
    }

    fun clear() {
        PREFIXES.forEach { runCatching { PrefHelper.removeAllWithPrefix(it) } }
        KEYS.forEach { runCatching { PrefHelper.clearKey<String>(it) } }
        val snapshot = synchronized(hooks) { hooks.toList() }
        snapshot.forEach { runCatching { it() } }
    }
}
