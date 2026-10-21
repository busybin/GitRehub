package io.github.busybin.gitrehub.compose.discussions

import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.core.model.Discussion
import io.github.busybin.gitrehub.core.net.ApiException
import io.github.busybin.gitrehub.compose.data.Discussions
import io.github.busybin.gitrehub.compose.data.DiscussionsDisabledException

/**
 * The discussions of one repository.
 *
 * A 404 is not treated as a generic failure: it is how GitHub answers when the repository simply
 * does not have Discussions enabled, and the screen says so instead of "404 not found".
 */
class DiscussionsViewModel : PagedViewModel<Discussion>() {

    private var owner: String = ""
    private var repo: String = ""

    fun setup(owner: String, repo: String) {
        if (this.owner == owner && this.repo == repo) return
        this.owner = owner
        this.repo = repo
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<Discussion> {
        if (owner.isBlank() || repo.isBlank()) return PageResult(emptyList(), false)
        return try {
            Discussions.list(owner, repo, page)
        } catch (t: Throwable) {
            val code = (t as? ApiException)?.error?.status
            if (code == 404 || code == 410) throw DiscussionsDisabledException() else throw t
        }
    }
}
