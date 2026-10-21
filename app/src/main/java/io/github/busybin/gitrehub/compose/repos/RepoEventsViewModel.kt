package io.github.busybin.gitrehub.compose.repos

import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMoreEvents

/**
 * Repo activity timeline (`GET /repos/{owner}/{repo}/events`), the content of
 * github.com/{owner}/{repo}/activity: pushes, branches, tags, PRs, issues, releases.
 *
 * GitHub only keeps the last 90 days / 300 events, so there is not much to page through.
 */
class RepoEventsViewModel : PagedViewModel<Event>() {

    private var owner = ""
    private var repo = ""

    /** Called once when this tab is first shown; refetches when the repo changes. */
    fun start(owner: String, repo: String) {
        if (this.owner == owner && this.repo == repo) return
        this.owner = owner
        this.repo = repo
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<Event> {
        val response = GitHubApi.repoEvents(owner, repo, page)
        // Deduplicate events by id (GitHub occasionally repeats one at a page boundary), newest first.
        val items = response.value.distinctBy { it.id }.sortedByDescending { it.createdAt }
        return PageResult(items, response.hasMoreEvents())
    }

    /** Show the disk cache first when offline or re-entering. */
    override suspend fun fetchCached(page: Int): PageResult<Event>? {
        val response = GitHubApi.repoEvents(owner, repo, page, cacheOnly = true)
        val items = response.value.distinctBy { it.id }.sortedByDescending { it.createdAt }
        if (items.isEmpty()) return null
        return PageResult(items, response.hasMoreEvents())
    }
}
