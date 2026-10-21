package io.github.busybin.gitrehub.compose.code

import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore

enum class RefKind { BRANCH, TAG }

/** A branch or a tag, reduced to what the list needs. */
data class RefItem(
    val name: String,
    val sha: String?,
    val message: String?,
    val isDefault: Boolean,
    val isProtected: Boolean,
)

/**
 * Branches and tags of a repository — one of the two things users explicitly complained the official
 * mobile client cannot even show ("missing a lot of features like seeing the release section of each
 * repo as well as branches").
 *
 * The list endpoints reduce `commit` to `sha`/`url` only, so there is no message here; the row shows
 * the name and the short sha.
 */
class BranchesViewModel : PagedViewModel<RefItem>() {

    private var owner: String = ""
    private var repo: String = ""
    private var kind: RefKind = RefKind.BRANCH

    fun setup(owner: String, repo: String, kind: RefKind) {
        if (this.owner == owner && this.repo == repo && this.kind == kind) return
        this.owner = owner
        this.repo = repo
        this.kind = kind
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<RefItem> {
        if (owner.isBlank() || repo.isBlank()) return PageResult(emptyList(), false)
        // Only needed to mark the default branch in the list; a failure must not kill the screen.
        val defaultBranch = runCatching { GitHubApi.repo(owner, repo).defaultBranch }.getOrNull()
        return when (kind) {
            RefKind.BRANCH -> {
                val response = GitHubApi.branches(owner, repo, page)
                val items = response.value.map { branch ->
                    RefItem(
                        name = branch.name,
                        sha = branch.commit?.sha,
                        message = branch.commit?.messageTitle?.takeIf { it.isNotBlank() },
                        isDefault = branch.name.equals(defaultBranch, ignoreCase = true),
                        isProtected = branch.isProtected,
                    )
                }
                PageResult(items, response.hasMore())
            }

            RefKind.TAG -> {
                val response = GitHubApi.tags(owner, repo, page)
                val items = response.value.map { tag ->
                    RefItem(
                        name = tag.name,
                        sha = tag.commit?.sha,
                        message = tag.commit?.messageTitle?.takeIf { it.isNotBlank() },
                        isDefault = false,
                        isProtected = false,
                    )
                }
                PageResult(items, response.hasMore())
            }
        }
    }
}
