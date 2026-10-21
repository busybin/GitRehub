package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.core.model.Discussion
import io.github.busybin.gitrehub.core.model.DiscussionComment
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore

/**
 * A repository has Discussions turned off: the API answers 404 and that is not an error the user
 * should read as "something broke" — it is a fact about the repository.
 */
class DiscussionsDisabledException : Exception("discussions are disabled for this repository")

/**
 * Coroutine facade over the (undocumented) discussions endpoints.
 *
 * Paging follows the app's usual convention: `per_page` of [GitHubApi.PAGE_SIZE], and the `Link`
 * header decides whether more pages exist.
 */
object Discussions {

    suspend fun list(owner: String, repo: String, page: Int): PageResult<Discussion> {
        val response = GitHubApi.discussions(owner, repo, page)
        return PageResult(response.value, response.hasMore())
    }

    suspend fun detail(owner: String, repo: String, number: Int): Discussion? =
        GitHubApi.discussion(owner, repo, number)

    suspend fun comments(owner: String, repo: String, number: Int, page: Int): PageResult<DiscussionComment> {
        val response = GitHubApi.discussionComments(owner, repo, number, page)
        return PageResult(response.value, response.hasMore())
    }
}
