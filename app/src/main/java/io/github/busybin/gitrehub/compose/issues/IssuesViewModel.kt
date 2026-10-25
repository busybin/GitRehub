package io.github.busybin.gitrehub.compose.issues

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.PageResult
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
    private var query: IssueQuery = IssueQuery()
    private var upstreamPage: Int = 0

    private val _options = MutableStateFlow(IssueFilterOptions())
    val options: StateFlow<IssueFilterOptions> = _options.asStateFlow()

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
        if (value == query.state) return
        query = query.copy(state = value)
        upstreamPage = 0
        refresh()
    }

    fun setQuery(value: IssueQuery) {
        if (value == query) return
        query = value
        upstreamPage = 0
        refresh()
    }

    fun currentFilter(): IssueFilter = query.state

    fun currentQuery(): IssueQuery = query

    fun currentKind(): IssueKind = kind

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
        val out = mutableListOf<Issue>()
        var more = false
        // Page upstream at most 5 times per fetch.
        var guard = 0
        while (out.size < GitHubApi.PAGE_SIZE && guard < 5) {
            guard++
            upstreamPage++
            val response = GitHubApi.repoIssues(
                owner = owner,
                repo = repo,
                state = query.state.apiValue,
                sort = query.sort.apiValue,
                page = upstreamPage,
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
        return PageResult(out, more)
    }

    private fun Issue.matchesKind(): Boolean =
        if (kind == IssueKind.PULL) isPullRequest else !isPullRequest
}
