package io.github.busybin.gitrehub.compose.notifications

import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone

/**
 * Notification filtering and grouping. The point is to see at a glance what involves me: GitHub's
 * `reason` says why a notification found me (@mention, assigned, review requested, ...).
 *
 * State is persisted in MMKV (see [NotificationFilterPrefs]); the pure parts avoid Android so they
 * can be unit-tested on the JVM.
 */

/** Top type chip. */
enum class NotificationFilter(@StringRes val labelRes: Int) {
    ALL(R.string.m3_notif_all),
    UNREAD(R.string.m3_notif_unread),
    ISSUE(R.string.m3_notif_issue),
    PULL(R.string.m3_notif_pr),
    COMMIT(R.string.m3_notif_commit),
    OTHER(R.string.m3_notif_other_type);

    fun matches(item: Notification): Boolean = when (this) {
        ALL -> true
        UNREAD -> item.unread
        ISSUE -> item.subject?.type == TYPE_ISSUE
        PULL -> item.subject?.type == TYPE_PULL
        COMMIT -> item.subject?.type == TYPE_COMMIT
        // Discussion / release / security alert have no dedicated chip, so "other" is the only way
        // to filter them out of "all".
        OTHER -> item.subject?.type?.let { it in KNOWN_TYPES } != true
    }

    private companion object {
        const val TYPE_ISSUE = "Issue"
        const val TYPE_PULL = "PullRequest"
        const val TYPE_COMMIT = "Commit"
        val KNOWN_TYPES = setOf(TYPE_ISSUE, TYPE_PULL, TYPE_COMMIT)
    }
}

/** Grouping of GitHub's `reason`, to reduce options: people mostly care whether they were addressed directly. */
enum class NotificationReasonGroup(@StringRes val labelRes: Int) {
    MENTIONS(R.string.m3_notif_reason_mentions),
    ASSIGNED(R.string.m3_notif_reason_assigned),
    REVIEW(R.string.m3_notif_reason_review),
    COMMENTS(R.string.m3_notif_reason_comments),
    STATE(R.string.m3_notif_reason_state),
    CI(R.string.m3_notif_reason_ci),
    SECURITY(R.string.m3_notif_reason_security),
    OTHER(R.string.m3_notif_reason_other);

    fun matches(reason: String?): Boolean = of(reason) == this

    companion object {
        fun of(reason: String?): NotificationReasonGroup = when (reason) {
            "mention", "team_mention" -> MENTIONS
            "assign" -> ASSIGNED
            "review_requested" -> REVIEW
            "comment", "author", "subscribed", "manual" -> COMMENTS
            "state_change" -> STATE
            "ci_activity" -> CI
            "security_alert" -> SECURITY
            // Unknown (null, invitation / approval_requested / push, or a new reason GitHub adds).
            else -> OTHER
        }
    }
}

/** How the list is grouped. Grouping is not a filter, so it is not counted in [NotificationFilterState.activeCount]. */
enum class NotificationGroup(@StringRes val labelRes: Int) {
    NONE(R.string.m3_notif_group_none),
    REPO(R.string.m3_notif_group_repo),
    TIME(R.string.m3_notif_group_time),
}

/** Time bucket. */
enum class NotificationTimeBucket(@StringRes val labelRes: Int) {
    TODAY(R.string.m3_notif_today),
    YESTERDAY(R.string.m3_notif_yesterday),
    RECENT(R.string.m3_notif_recent),
    OLDER(R.string.m3_notif_older);

    companion object {
        /** [now] and [zone] are injectable for tests; the UI uses the defaults. */
        fun of(
            at: Instant?,
            now: Long = System.currentTimeMillis(),
            zone: TimeZone = TimeZone.getDefault(),
        ): NotificationTimeBucket {
            if (at == null) return OLDER
            val time = at.toEpochMilli()
            val todayStart = startOfDay(now, zone)
            val yesterdayStart = startOfDay(todayStart - ONE_DAY, zone)
            return when {
                time >= todayStart -> TODAY
                time >= yesterdayStart -> YESTERDAY
                time >= todayStart - 7 * ONE_DAY -> RECENT
                else -> OLDER
            }
        }

        private const val ONE_DAY = 24L * 60 * 60 * 1000

        private fun startOfDay(time: Long, zone: TimeZone): Long =
            Calendar.getInstance(zone).apply {
                timeInMillis = time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
    }
}

/** All active notification conditions; the default filters and groups nothing. */
data class NotificationFilterState(
    val type: NotificationFilter = NotificationFilter.ALL,
    val repo: String? = null,
    val reasons: Set<NotificationReasonGroup> = emptySet(),
    val group: NotificationGroup = NotificationGroup.NONE,
) {

    /** Badge count: how many conditions are active (grouping excluded). */
    val activeCount: Int
        get() = (if (type != NotificationFilter.ALL) 1 else 0) +
            (if (repo != null) 1 else 0) +
            (if (reasons.isNotEmpty()) 1 else 0)

    /** Repo + reason only, excluding type: chip counts must use this, otherwise a repo filter would still show site-wide totals. */
    fun narrows(item: Notification): Boolean =
        (repo == null || item.repoFullName == repo) &&
            (reasons.isEmpty() || reasons.any { it.matches(item.reason) })

    fun matches(item: Notification): Boolean = type.matches(item) && narrows(item)
}

/** One group section: [repoName] for repo grouping, [bucket] for time grouping, both null otherwise. */
data class NotificationSection(
    val key: String,
    val repoName: String? = null,
    val bucket: NotificationTimeBucket? = null,
    val items: List<Notification>,
)

/** Split a filtered list into sections, keeping order within each; repo sections are name-sorted with the no-repo bucket last. */
fun sectionNotifications(
    items: List<Notification>,
    group: NotificationGroup,
    now: Long = System.currentTimeMillis(),
): List<NotificationSection> = when (group) {
    NotificationGroup.NONE -> listOf(NotificationSection(key = "all", items = items))

    NotificationGroup.REPO -> items
        .groupBy { it.repoFullName.orEmpty() }
        .toList()
        .sortedWith(
            compareBy<Pair<String, List<Notification>>> { (repo, _) -> repo.isEmpty() }
                .thenBy { (repo, _) -> repo.lowercase() },
        )
        .map { (repo, list) ->
            NotificationSection(
                key = "repo:$repo",
                repoName = repo.ifEmpty { null },
                items = list,
            )
        }

    NotificationGroup.TIME -> NotificationTimeBucket.entries
        .map { bucket ->
            NotificationSection(
                key = "time:${bucket.name}",
                bucket = bucket,
                items = items.filter { NotificationTimeBucket.of(it.updatedAt, now) == bucket },
            )
        }
        .filter { it.items.isNotEmpty() }
}

/** Persist filters/grouping in MMKV (keys prefixed `m3_`); default values are not written. */
object NotificationFilterPrefs {

    private const val KEY_TYPE = "m3_notif_filter_type"
    private const val KEY_REPO = "m3_notif_filter_repo"
    private const val KEY_REASONS = "m3_notif_filter_reasons"
    private const val KEY_GROUP = "m3_notif_group"

    private val _filter = MutableStateFlow(load())
    val filter: StateFlow<NotificationFilterState> = _filter.asStateFlow()

    fun setType(type: NotificationFilter) = update { it.copy(type = type) }

    fun setRepo(repo: String?) = update { it.copy(repo = repo) }

    fun toggleReason(reason: NotificationReasonGroup) = update {
        it.copy(reasons = if (reason in it.reasons) it.reasons - reason else it.reasons + reason)
    }

    fun setGroup(group: NotificationGroup) = update { it.copy(group = group) }

    /** "Clear filter": type/repo/reason reset; grouping is kept since it is only a display choice. */
    fun clearFilter() = update {
        it.copy(
            type = NotificationFilter.ALL,
            repo = null,
            reasons = emptySet(),
        )
    }

    private inline fun update(transform: (NotificationFilterState) -> NotificationFilterState) {
        val next = transform(_filter.value)
        if (next == _filter.value) return
        persist(next)
        _filter.value = next
    }

    private fun persist(state: NotificationFilterState) {
        put(KEY_TYPE, state.type.takeIf { it != NotificationFilter.ALL }?.name.orEmpty())
        put(KEY_REPO, state.repo.orEmpty())
        put(KEY_REASONS, state.reasons.joinToString(",") { it.name })
        put(KEY_GROUP, state.group.takeIf { it != NotificationGroup.NONE }?.name.orEmpty())
    }

    private fun load(): NotificationFilterState {
        val defaults = NotificationFilterState()
        return NotificationFilterState(
            type = parseEnum(PrefHelper.getString(KEY_TYPE), defaults.type),
            repo = PrefHelper.getString(KEY_REPO)?.takeIf { it.isNotBlank() },
            reasons = parseEnumSet<NotificationReasonGroup>(PrefHelper.getString(KEY_REASONS)),
            group = parseEnum(PrefHelper.getString(KEY_GROUP), defaults.group),
        )
    }

    private fun put(key: String, value: String) {
        if (value.isEmpty()) PrefHelper.clearKey<String>(key) else PrefHelper.putAny(key, value)
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T =
        T::class.java.enumConstants?.firstOrNull { it.name == raw } ?: fallback

    private inline fun <reified T : Enum<T>> parseEnumSet(raw: String?): Set<T> {
        if (raw.isNullOrBlank()) return emptySet()
        val names = raw.split(',').map { it.trim() }.toSet()
        return T::class.java.enumConstants?.filter { it.name in names }?.toSet().orEmpty()
    }
}
