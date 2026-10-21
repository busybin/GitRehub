package io.github.busybin.gitrehub.compose.issues

import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore

enum class IssueFilter(@StringRes val labelRes: Int, val apiValue: String) {
    OPEN(R.string.m3_issue_open, "open"),
    CLOSED(R.string.m3_issue_closed, "closed"),
}

/** Issues and PRs are split apart: GitHub's issues endpoint returns both, filtered here by payload. */
enum class IssueKind(@StringRes val labelRes: Int) {
    ISSUE(R.string.m3_issues),
    PULL(R.string.m3_pulls),
}

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
    private var filter: IssueFilter = IssueFilter.OPEN
    private var kind: IssueKind = IssueKind.ISSUE
    private var upstreamPage: Int = 0

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
        if (value == filter) return
        filter = value
        upstreamPage = 0
        refresh()
    }

    fun currentFilter(): IssueFilter = filter

    fun currentKind(): IssueKind = kind

    override suspend fun fetch(page: Int): PageResult<Issue> {
        if (owner.isBlank() || repo.isBlank()) return PageResult(emptyList(), false)
        val out = mutableListOf<Issue>()
        var more = false
        // Page upstream at most 5 times per fetch.
        var guard = 0
        while (out.size < GitHubApi.PAGE_SIZE && guard < 5) {
            guard++
            upstreamPage++
            val response = GitHubApi.repoIssues(owner, repo, filter.apiValue, "created", upstreamPage)
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
