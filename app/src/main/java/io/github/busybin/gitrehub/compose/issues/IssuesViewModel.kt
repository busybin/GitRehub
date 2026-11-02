package io.github.busybin.gitrehub.compose.issues

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.model.Label
import io.github.busybin.gitrehub.core.model.Milestone
import io.github.busybin.gitrehub.core.model.User
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class IssueFilter(@StringRes val labelRes: Int, val apiValue: String) {
    OPEN(R.string.m3_issue_open, "open"),
    CLOSED(R.string.m3_issue_closed, "closed"),
    ALL(R.string.m3_issue_all, "all"),
}

enum class IssueSort(@StringRes val labelRes: Int, val apiValue: String) {
    CREATED(R.string.m3_issue_sort_newest, "created"),
    UPDATED(R.string.m3_issue_sort_updated, "updated"),
    COMMENTS(R.string.m3_issue_sort_comments, "comments"),
}

/** Issues and PRs are split apart: GitHub's issues endpoint returns both, filtered here by payload. */
enum class IssueKind(@StringRes val labelRes: Int) {
    ISSUE(R.string.m3_issues),
    PULL(R.string.m3_pulls),
}

/**
 * Everything the issues endpoint can filter on. GitHub applies these server side, so paging stays
 * exact; only [IssueKind] is filtered on the client (the endpoint mixes issues and pull requests).
 */
data class IssueQuery(
    val state: IssueFilter = IssueFilter.OPEN,
    val sort: IssueSort = IssueSort.CREATED,
    val descending: Boolean = true,
    val labels: Set<String> = emptySet(),
    /** Login, or [NOBODY] for "assigned to nobody". */
    val assignee: String? = null,
    /** Milestone number, or [NO_MILESTONE]. */
    val milestone: Int? = null,
) {
    val activeCount: Int
        get() = (if (labels.isEmpty()) 0 else 1) +
            (if (assignee == null) 0 else 1) +
            (if (milestone == null) 0 else 1) +
            (if (sort != IssueSort.CREATED) 1 else 0) +
            (if (!descending) 1 else 0)

    companion object {
        const val NOBODY = "*"
        const val NO_MILESTONE = -1
    }
}

data class IssueFilterOptions(
    val loading: Boolean = false,
    val failed: Boolean = false,
    val labels: List<Label> = emptyList(),
    val assignees: List<User> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
)

/**
 * Issues *or* pull requests of one repository (GitHub's issues endpoint returns both, so the rows
 * are filtered to the requested [IssueKind]).
 *
 * Filtering is client-side, so a 30-row page may be mostly dropped. `upstreamPage` is a separate
 * cursor: one `fetch` keeps paging upstream until a full filtered page (or the end) is reached.
 * Do not reuse the PagedViewModel page number as the upstream page, or pages get fetched twice.
 */
class IssuesViewModel : PagedViewModel<Issue>() {

    private var owner: String = ""
    private var repo: String = ""
    private var kind: IssueKind = IssueKind.ISSUE
    private var upstreamPage: Int = 0

    /** Observable, so the filter chips follow a tap even before the list answers. */
    private val _query = MutableStateFlow(IssueQuery())
    val query: StateFlow<IssueQuery> = _query.asStateFlow()

    private var currentQuery: IssueQuery
        get() = _query.value
        set(value) { _query.value = value }

    private val _options = MutableStateFlow(IssueFilterOptions())
    val options: StateFlow<IssueFilterOptions> = _options.asStateFlow()

    private val _bulkBusy = MutableStateFlow(false)
    val bulkBusy: StateFlow<Boolean> = _bulkBusy.asStateFlow()

    /** Result of a bulk action ("3 done" / "2 done, 1 failed"); the list clears it after showing. */
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun setup(owner: String, repo: String, kind: IssueKind = IssueKind.ISSUE) {
        if (this.owner == owner && this.repo == repo) {
            if (this.kind != kind) {
                this.kind = kind
                upstreamPage = 0
                refresh()
            }
            return
        }
        this.owner = owner
        this.repo = repo
        this.kind = kind
        upstreamPage = 0
        refresh()
    }

    fun setFilter(value: IssueFilter) {
        if (value == currentQuery.state) return
        currentQuery = currentQuery.copy(state = value)
        refresh()
    }

    fun setQuery(value: IssueQuery) {
        if (value == currentQuery) return
        currentQuery = value
        refresh()
    }

    /** Loaded lazily, the first time the filter sheet opens. */
    fun loadOptions() {
        if (_options.value.loading || _options.value.labels.isNotEmpty()) return
        viewModelScope.launch {
            _options.value = _options.value.copy(loading = true, failed = false)
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    IssueFilterOptions(
                        labels = GitHubApi.repoLabels(owner, repo),
                        assignees = GitHubApi.assignableUsers(owner, repo),
                        milestones = runCatching { GitHubApi.repoMilestones(owner, repo) }
                            .getOrDefault(emptyList()),
                    )
                }
            }
            _options.value = loaded.getOrElse {
                _options.value.copy(loading = false, failed = true)
            }
        }
    }

    override suspend fun fetch(page: Int): PageResult<Issue> {
        if (owner.isBlank() || repo.isBlank()) return PageResult(emptyList(), false)
        val query = currentQuery
        val out = mutableListOf<Issue>()
        var more = false
        // A refresh restarts upstream; loadMore continues where the last successful fetch stopped.
        var cursor = if (page <= 1) 0 else upstreamPage
        var guard = 0
        while (out.size < GitHubApi.PAGE_SIZE) {
            // The issues endpoint returns pull requests too and the split is client-side, so an
            // "issues" page can come back empty while upstream still has rows. Scan further in
            // that case (up to EMPTY_SCAN_PAGES) instead of showing an empty tab.
            if (guard >= if (out.isEmpty()) EMPTY_SCAN_PAGES else MAX_PAGES) break
            guard++
            cursor++
            val response = GitHubApi.repoIssues(
                owner = owner,
                repo = repo,
                state = query.state.apiValue,
                sort = query.sort.apiValue,
                page = cursor,
                direction = if (query.descending) "desc" else "asc",
                labels = query.labels.toList(),
                assignee = query.assignee,
                milestone = query.milestone,
            )
            val items = response.value
            out += items.filter { it.matchesKind() }
            more = response.hasMore()
            if (!more) break
        }
        // Only a fetch that came back commits the cursor, so a failed page is retried, not skipped.
        upstreamPage = cursor
        // An empty page must not advertise more rows: there is nothing to scroll, so the screen
        // would never ask for the next page.
        return PageResult(out, more && out.isNotEmpty())
    }

    private fun Issue.matchesKind(): Boolean =
        if (kind == IssueKind.PULL) isPullRequest else !isPullRequest

    private companion object {
        /** Upstream pages per fetch when rows are being found (fills one page of rows). */
        const val MAX_PAGES = 5

        /** Extra scan while every upstream row belongs to the other kind. */
        const val EMPTY_SCAN_PAGES = 20
    }

    /** Close or reopen every selected thread; PRs go through the same issues endpoint. */
    fun bulkClose(numbers: Set<Int>, closed: Boolean) = runBulk(numbers) { number ->
        GitHubApi.setIssueState(owner, repo, number, closed)
    }

    /**
     * Add one label to every selected thread. The API replaces the whole set, so the caller passes
     * the labels each thread already has.
     */
    fun bulkAddLabel(
        numbers: Set<Int>,
        label: String,
        existing: Map<Int, List<String>>,
    ) = runBulk(numbers) { number ->
        val current = existing[number].orEmpty()
        if (label !in current) {
            GitHubApi.setIssueLabels(owner, repo, number, current + label)
        }
    }

    /** Assignees are additive on GitHub, so the whole selection can share one call shape. */
    fun bulkAssign(numbers: Set<Int>, logins: List<String>) {
        if (logins.isEmpty()) return
        runBulk(numbers) { number -> GitHubApi.addAssignees(owner, repo, number, logins) }
    }

    /**
     * Runs one write per thread, sequentially: a burst of parallel writes is what triggers GitHub's
     * secondary rate limit. Failures are counted, never thrown away silently.
     */
    private fun runBulk(numbers: Set<Int>, block: suspend (Int) -> Unit) {
        if (_bulkBusy.value || numbers.isEmpty()) return
        _bulkBusy.value = true
        viewModelScope.launch {
            var failed = 0
            withContext(Dispatchers.IO) {
                numbers.forEach { number ->
                    runCatching { block(number) }.onFailure { failed++ }
                }
            }
            _bulkBusy.value = false
            _message.value = if (failed == 0) {
                UiText.res(R.string.m3_bulk_done, numbers.size)
            } else {
                UiText.res(R.string.m3_bulk_partial, numbers.size - failed, failed)
            }
            refresh()
        }
    }
}
