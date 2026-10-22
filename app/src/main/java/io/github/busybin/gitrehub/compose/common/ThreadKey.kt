package io.github.busybin.gitrehub.compose.common

import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.Issue

/**
 * Stable identifier for a GitHub "thread" (the thing a notification and a feed event both point at).
 *
 * The modern UI keeps one shared read state: reading something on the notifications screen marks the
 * matching row in the activity feed as read too (and the other way around). Raw ids cannot be used
 * for that — a [io.github.busybin.gitrehub.core.model.Notification] has a notification id while an
 * [Event] has an event id, and neither knows about the other. What both *do* know is which
 * issue/pull request/release they are about, so the key is built from
 * `owner/repo` + kind + number:
 *
 * - issue    -> `octocat/Hello-World#i42`
 * - PR       -> `octocat/Hello-World#p42`
 * - release  -> `octocat/Hello-World#r123456`
 *
 * Events that carry no thread (stars, forks, ...) have no key and simply fall back to the
 * event-id based bookkeeping; pushes get a [ofPush] key because they are by far the most common
 * rows and the id-only bookkeeping kept re-surfacing them as unread.
 */
object ThreadKey {

    const val ISSUE = 'i'
    const val PULL_REQUEST = 'p'
    const val RELEASE = 'r'

    fun of(repoFullName: String?, kind: Char, number: Long?): String? {
        if (repoFullName.isNullOrBlank() || number == null || number <= 0L) return null
        return "$repoFullName#$kind$number"
    }

    /**
     * Parses a notification subject API url, e.g.
     * `https://api.github.com/repos/octocat/Hello-World/issues/42`.
     * Returns null for thread types we do not map (commits, discussions, ...).
     */
    fun fromApiUrl(apiUrl: String?): String? {
        if (apiUrl.isNullOrBlank()) return null
        val parts = apiUrl.removeSuffix("/").split("/")
        val repos = parts.indexOf("repos")
        if (repos < 0 || parts.size < repos + 5) return null
        val repo = "${parts[repos + 1]}/${parts[repos + 2]}"
        val number = parts[repos + 4].toLongOrNull()
        return when (parts[repos + 3]) {
            "issues" -> of(repo, ISSUE, number)
            "pulls" -> of(repo, PULL_REQUEST, number)
            "releases" -> of(repo, RELEASE, number)
            else -> null
        }
    }

    /** The thread an issue/pull request row is about (works for search results, see [repoFullName]). */
    fun ofIssue(issue: Issue): String? = of(
        repoFullName = issue.repoFullName,
        kind = if (issue.pullRequest != null) PULL_REQUEST else ISSUE,
        number = issue.number.toLong(),
    )

    /**
     * Whether a notification counts as read because the same thread was read elsewhere (feed,
     * another entry of the list): the key must be recorded, at or after the thread's last update.
     * A key without a timestamp only counts when no timestamps exist at all — that is the pre-timing
     * legacy state; with timestamps present, a missing entry must not borrow another thread's time.
     */
    fun isReadLocally(
        apiUrl: String?,
        updatedAt: Long?,
        keys: Set<String>,
        readAt: Map<String, Long>,
    ): Boolean = isRead(fromApiUrl(apiUrl), updatedAt, keys, readAt)

    /** Timestamp-aware check for a key resolved elsewhere (e.g. [ofIssue]). */
    fun isRead(
        key: String?,
        updatedAt: Long?,
        keys: Set<String>,
        readAt: Map<String, Long>,
    ): Boolean {
        if (key == null || key !in keys) return false
        val at = readAt[key]
        if (at == null) return readAt.isEmpty()
        return updatedAt == null || updatedAt <= at
    }

    /**
     * Stable content key for a push event. Pushes have no thread, so the feed used to rely on the
     * event id alone — and pushes are the rows users keep seeing unread again (event ids get dropped
     * from the bounded id table, and the same push may be served by two event streams).
     *
     * `push_id` identifies the push itself, so a card read once stays read even if the event id
     * changes; the head sha is the fallback for payloads that were trimmed.
     */
    fun ofPush(event: Event): String? {
        event.payload.pushId.takeIf { it != 0L }?.let { return "push:$it" }
        val repo = event.repoFullName ?: return null
        val head = event.payload.head?.takeIf { it.isNotBlank() } ?: return null
        return "push:$repo@$head"
    }

    /** The thread an activity feed event is about, or null when it is not thread based. */
    fun ofEvent(event: Event): String? {
        // Must use repoFullName: the event stream's mini repo object has no full_name (only `name`
        // whose value is owner/repo), so reading event.repo?.fullName is always null and an opened
        // issue/PR would not become read on the notifications screen.
        val repo = event.repoFullName
        val payload = event.payload
        payload.pullRequest?.number?.takeIf { it > 0 }?.let { return of(repo, PULL_REQUEST, it.toLong()) }
        payload.issue?.number?.takeIf { it > 0 }?.let { return of(repo, ISSUE, it.toLong()) }
        payload.release?.id?.takeIf { it > 0 }?.let { return of(repo, RELEASE, it) }
        return null
    }
}
