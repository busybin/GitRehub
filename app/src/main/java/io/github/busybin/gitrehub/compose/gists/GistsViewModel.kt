package io.github.busybin.gitrehub.compose.gists

import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.core.model.Gist
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore

/**
 * Gists of the signed-in account (`login == null`) or of any user.
 *
 * Gists are a GitHub feature the official mobile app does not show at all; the list endpoint
 * already carries each file's text, so opening one needs no second request.
 */
class GistsViewModel : PagedViewModel<Gist>() {

    private var login: String? = null
    private var started = false

    fun setup(login: String?) {
        if (started && this.login == login) return
        this.login = login
        started = true
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<Gist> {
        val response = login?.takeIf { it.isNotBlank() }
            ?.let { GitHubApi.userGists(it, page) }
            ?: GitHubApi.myGists(page)
        return PageResult(response.value, response.hasMore())
    }
}
