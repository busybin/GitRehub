package io.github.busybin.gitrehub.compose.code

import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.core.model.Commit
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore

/**
 * Commit history of one file or directory (`GET /commits?path=`).
 *
 * The repository-wide history answers "what happened lately"; this answers "who touched this file
 * and why", which is what a reader actually asks after finding a line.
 */
class FileHistoryViewModel : PagedViewModel<Commit>() {

    private var owner: String = ""
    private var repo: String = ""
    private var path: String = ""
    private var ref: String = ""

    fun setup(owner: String, repo: String, path: String, ref: String?) {
        val target = ref.orEmpty()
        if (this.owner == owner && this.repo == repo && this.path == path && this.ref == target) return
        this.owner = owner
        this.repo = repo
        this.path = path
        this.ref = target
        refresh()
    }

    override suspend fun prepare() {
        if (ref.isBlank() && owner.isNotBlank() && repo.isNotBlank()) {
            ref = runCatching { GitHubApi.repo(owner, repo).defaultBranch.orEmpty() }.getOrDefault("")
        }
    }

    override suspend fun fetch(page: Int): PageResult<Commit> {
        if (owner.isBlank() || repo.isBlank() || path.isBlank()) return PageResult(emptyList(), false)
        val response = GitHubApi.commits(owner, repo, ref, page, path = path)
        return PageResult(response.value, response.hasMore())
    }
}
