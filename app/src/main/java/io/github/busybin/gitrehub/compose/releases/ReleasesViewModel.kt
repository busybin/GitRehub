package io.github.busybin.gitrehub.compose.releases

import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.core.model.Release
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore

/**
 * Releases / tags of a repository.
 *
 * Directly answers the most repeated complaint about the official mobile app: users can not even
 * *see* the release section of a repository there.
 */
class ReleasesViewModel : PagedViewModel<Release>() {

    private var owner: String = ""
    private var repo: String = ""

    fun setup(owner: String, repo: String) {
        if (this.owner == owner && this.repo == repo) return
        this.owner = owner
        this.repo = repo
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<Release> {
        if (owner.isBlank() || repo.isBlank()) return PageResult(emptyList(), false)
        val response = GitHubApi.releases(owner, repo, page)
        return PageResult(response.value, response.hasMore())
    }
}
