package io.github.busybin.gitrehub.compose.code

import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.core.model.Commit
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore

/**
 * Commits on a ref (branch / tag / commit), for the commit history screen.
 *
 * When ref is empty the repo's default branch is fetched first: an empty `?sha=` is rejected by GitHub
 * as an invalid parameter.
 */
class CommitsViewModel : PagedViewModel<Commit>() {

    private var owner: String = ""
    private var repo: String = ""
    private var ref: String = ""

    fun setup(owner: String, repo: String, ref: String) {
        if (this.owner == owner && this.repo == repo && this.ref == ref) return
        this.owner = owner
        this.repo = repo
        this.ref = ref
        refresh()
    }

    fun currentRef(): String = ref

    override suspend fun prepare() {
        if (ref.isBlank() && owner.isNotBlank() && repo.isNotBlank()) {
            ref = runCatching { GitHubApi.repo(owner, repo).defaultBranch.orEmpty() }.getOrDefault("")
        }
    }

    override suspend fun fetch(page: Int): PageResult<Commit> {
        if (owner.isBlank() || repo.isBlank() || ref.isBlank()) return PageResult(emptyList(), false)
        val response = GitHubApi.commits(owner, repo, ref, page)
        return PageResult(response.value, response.hasMore())
    }
}
