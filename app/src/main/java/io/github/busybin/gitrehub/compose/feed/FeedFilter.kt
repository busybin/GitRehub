package io.github.busybin.gitrehub.compose.feed

import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.ThreadKey
import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.EventType

/**
 * All feed filter dimensions plus the pure "does this event pass" predicate.
 * Pure Kotlin (no Compose, no PrefHelper) so it is unit-testable on the JVM; persisted by [FeedPrefs].
 */

/** Coarse event group: GitHub has 26 event types, grouped by what users care about. */
enum class FeedEventGroup(@StringRes val labelRes: Int) {
    PUSH(R.string.m3_group_push),
    STAR(R.string.m3_group_star),
    ISSUE(R.string.m3_group_issue),
    PULL_REQUEST(R.string.m3_group_pr),
    COMMENT(R.string.m3_group_comment),
    RELEASE(R.string.m3_group_release),
    WIKI(R.string.m3_group_wiki),
    GIST(R.string.m3_group_gist),
    FOLLOW(R.string.m3_group_follow),
    REPO(R.string.m3_group_repo),
    OTHER(R.string.m3_group_other),
}

/** Which group an event belongs to. Unknown types fall into OTHER and are not filtered out because of that. */
fun EventType.group(): FeedEventGroup = when (this) {
    EventType.PushEvent, EventType.DownloadEvent -> FeedEventGroup.PUSH
    EventType.WatchEvent, EventType.ForkEvent, EventType.ForkApplyEvent -> FeedEventGroup.STAR
    EventType.IssuesEvent -> FeedEventGroup.ISSUE
    EventType.PullRequestEvent -> FeedEventGroup.PULL_REQUEST
    EventType.IssueCommentEvent,
    EventType.CommitCommentEvent,
    EventType.PullRequestReviewCommentEvent,
    EventType.PullRequestReviewEvent,
    -> FeedEventGroup.COMMENT
    EventType.ReleaseEvent -> FeedEventGroup.RELEASE
    EventType.GollumEvent -> FeedEventGroup.WIKI
    EventType.GistEvent -> FeedEventGroup.GIST
    EventType.FollowEvent, EventType.MemberEvent, EventType.TeamAddEvent -> FeedEventGroup.FOLLOW
    EventType.CreateEvent,
    EventType.DeleteEvent,
    EventType.PublicEvent,
    EventType.RepositoryEvent,
    EventType.OrgBlockEvent,
    EventType.OrganizationEvent,
    -> FeedEventGroup.REPO
    EventType.ProjectEvent,
    EventType.ProjectCardEvent,
    EventType.ProjectColumnEvent,
    EventType.Unknown,
    -> FeedEventGroup.OTHER
}

enum class FeedTimeRange(@StringRes val labelRes: Int, val windowMillis: Long?) {
    ALL(R.string.m3_time_all, null),
    TODAY(R.string.m3_time_today, 24L * 3600_000),
    THREE_DAYS(R.string.m3_time_3d, 3L * 24 * 3600_000),
    WEEK(R.string.m3_time_7d, 7L * 24 * 3600_000),
    MONTH(R.string.m3_time_30d, 30L * 24 * 3600_000),
}

/** Which of the three merged streams an event came from (see FeedViewModel). */
enum class FeedSource(@StringRes val labelRes: Int) {
    ALL(R.string.m3_source_all),
    MINE(R.string.m3_source_mine),
    INTERACTIONS(R.string.m3_source_interactions),
}

enum class FeedReadFilter(@StringRes val labelRes: Int) {
    ALL(R.string.m3_read_all),
    UNREAD(R.string.m3_read_unread),
}

enum class FeedOrder(@StringRes val labelRes: Int) {
    NEWEST(R.string.m3_order_newest),
    OLDEST(R.string.m3_order_oldest),
}

/** All active filter conditions; the default value applies no filtering, so "clear filter" resets to it. */
data class FeedFilterState(
    val groups: Set<FeedEventGroup> = FeedEventGroup.entries.toSet(),
    val timeRange: FeedTimeRange = FeedTimeRange.ALL,
    val source: FeedSource = FeedSource.ALL,
    val readFilter: FeedReadFilter = FeedReadFilter.ALL,
    val order: FeedOrder = FeedOrder.NEWEST,
    val hideBots: Boolean = false,
    val keywords: List<String> = emptyList(),
) {

    val isDefault: Boolean
        get() = this == Default

    /** Badge count on the top bar: how many conditions are active (keywords count as one). */
    val activeCount: Int
        get() {
            var count = 0
            if (groups != FeedEventGroup.entries.toSet()) count++
            if (timeRange != FeedTimeRange.ALL) count++
            if (source != FeedSource.ALL) count++
            if (readFilter != FeedReadFilter.ALL) count++
            if (order != FeedOrder.NEWEST) count++
            if (hideBots) count++
            if (keywords.isNotEmpty()) count++
            return count
        }

    companion object {
        val Default = FeedFilterState()
    }
}

/** Bot accounts: GitHub ends their login with `[bot]` (dependabot[bot], github-actions[bot]). */
private fun String?.isBot(): Boolean = this?.endsWith("[bot]", ignoreCase = true) == true

/**
 * Whether this event should be shown.
 *
 * @param me current login, used for [FeedSource.MINE] and [FeedSource.INTERACTIONS]
 *   (owner is me, actor is not).
 * @param isRead computed by the caller (event id or thread key, see `ReadState`), so this stays pure.
 */
fun Event.matchesFilter(
    filter: FeedFilterState,
    me: String?,
    isRead: Boolean,
): Boolean {
    if (filter.groups.isNotEmpty() && type.group() !in filter.groups) return false

    if (filter.hideBots && actor?.login.isBot()) return false

    filter.timeRange.windowMillis?.let { window ->
        val at = createdAt?.toEpochMilli() ?: return@let
        if (System.currentTimeMillis() - at > window) return false
    }

    when (filter.source) {
        FeedSource.ALL -> Unit
        FeedSource.MINE -> if (me == null || actor?.login != me) return false
        FeedSource.INTERACTIONS -> {
            val owner = repoOwner
            if (me == null || owner != me || actor?.login == me) return false
        }
    }

    when (filter.readFilter) {
        FeedReadFilter.ALL -> Unit
        FeedReadFilter.UNREAD -> if (isRead) return false
    }

    if (filter.keywords.isNotEmpty()) {
        val haystack = listOfNotNull(actor?.login, repoFullName, repo?.name, titleLine())
        if (haystack.any { text -> filter.keywords.any { text.contains(it, ignoreCase = true) } }) {
            return false
        }
    }

    return true
}

/** Read check (event id, thread key or push key), same rule as `ReadState`; shared by the predicate and the UI. */
fun isEventRead(
    event: Event,
    readIds: Set<Long>,
    readKeys: Set<String>,
    readBefore: Long = 0L,
    readPushes: Set<String> = emptySet(),
): Boolean =
    readIds.contains(event.id) ||
        readKeys.contains(ThreadKey.ofEvent(event)) ||
        ThreadKey.ofPush(event)?.let { it in readPushes } == true ||
        (readBefore > 0L && (event.createdAt?.toEpochMilli() ?: Long.MAX_VALUE) <= readBefore)

/** Comma, semicolon and whitespace all separate keywords. */
fun parseKeywords(raw: String): List<String> = raw
    .split(',', ';', '\n', '\t', ' ')
    .map { it.trim() }
    .filter { it.isNotEmpty() }

fun joinKeywords(keywords: List<String>): String = keywords.joinToString(", ")
