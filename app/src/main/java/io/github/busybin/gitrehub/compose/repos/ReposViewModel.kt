package io.github.busybin.gitrehub.compose.repos

import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.compose.common.SavedPrefs
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Repo
import io.github.busybin.gitrehub.core.net.ApiResponse
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

enum class RepoFilter(@StringRes val labelRes: Int) {
    MINE(R.string.m3_repos_mine),
    STARRED(R.string.m3_repos_starred),
    FAVORITE(R.string.m3_repos_favorites),
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
        if (filter == RepoFilter.FAVORITE) return favoritePage(page)
        val response = when (filter) {
            RepoFilter.MINE -> GitHubApi.myRepos(page, cacheOnly)
            RepoFilter.STARRED -> {
                val user = login ?: throw IllegalStateException("not signed in")
                GitHubApi.starredRepos(user, page, cacheOnly)
            }
            RepoFilter.FAVORITE -> error("handled above")
        }
        return response.toPage()
    }
}

/**
 * Favourites are local names, not a server list: fetch each one (in parallel, first page only) and
 * drop the ones that no longer exist or are not visible to this token.
 */
private suspend fun favoritePage(page: Int): PageResult<Repo> {
    if (page > 1) return PageResult(emptyList(), false)
    val names = SavedPrefs.favorites.value.take(60)
    if (names.isEmpty()) return PageResult(emptyList(), false)
    val repos = withContext(Dispatchers.IO) {
        names.map { name ->
            async {
                val owner = name.substringBefore('/')
                val repo = name.substringAfter('/', "")
                if (owner.isBlank() || repo.isBlank()) null
                else runCatching { GitHubApi.repo(owner, repo) }.getOrNull()
            }
        }.awaitAll()
    }
    return PageResult(repos.filterNotNull(), false)
}

/** Shared by the repo list screens: rows plus "is there another page" from `Link`. */
internal fun ApiResponse<List<Repo>>.toPage(): PageResult<Repo> = PageResult(value, hasMore())
