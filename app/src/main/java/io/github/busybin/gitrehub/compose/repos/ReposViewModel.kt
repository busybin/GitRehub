package io.github.busybin.gitrehub.compose.repos

import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Repo
import io.github.busybin.gitrehub.core.net.ApiResponse
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore

enum class RepoFilter(@StringRes val labelRes: Int) {
    MINE(R.string.m3_repos_mine),
    STARRED(R.string.m3_repos_starred),
}

class ReposViewModel : PagedViewModel<Repo>() {

    var filter: RepoFilter = RepoFilter.MINE
        private set

    private var login: String? = null

    override suspend fun prepare() {
        if (login == null) login = Session.currentAccount()?.login
    }

    fun setFilter(value: RepoFilter) {
        if (value == filter) return
        filter = value
        refresh()
    }

    override suspend fun fetch(page: Int): PageResult<Repo> = page(page, cacheOnly = false)

    override suspend fun fetchCached(page: Int): PageResult<Repo>? =
        page(page, cacheOnly = true).takeIf { it.items.isNotEmpty() }

    private suspend fun page(page: Int, cacheOnly: Boolean): PageResult<Repo> {
        val response = when (filter) {
            RepoFilter.MINE -> GitHubApi.myRepos(page, cacheOnly)
            RepoFilter.STARRED -> {
                val user = login ?: throw IllegalStateException("not signed in")
                GitHubApi.starredRepos(user, page, cacheOnly)
            }
        }
        return response.toPage()
    }
}

/** Shared by the repo list screens: rows plus "is there another page" from `Link`. */
internal fun ApiResponse<List<Repo>>.toPage(): PageResult<Repo> = PageResult(value, hasMore())
