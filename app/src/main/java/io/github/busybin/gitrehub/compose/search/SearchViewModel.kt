package io.github.busybin.gitrehub.compose.search

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.model.Repo
import io.github.busybin.gitrehub.core.model.SearchCode
import io.github.busybin.gitrehub.core.model.User
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The four GitHub search scopes: repositories, users, issues/PRs and code.
 * Code search needs a `repo:`/`user:`/`org:` qualifier, otherwise GitHub answers 422.
 */
enum class SearchScope(@StringRes val labelRes: Int) {
    REPOS(R.string.m3_profile_repos),
    USERS(R.string.m3_users),
    ISSUES(R.string.m3_search_issues),
    CODE(R.string.m3_search_code),
}

sealed interface SearchItem {
    data class RepoHit(val repo: Repo) : SearchItem
    data class UserHit(val user: User) : SearchItem
    data class IssueHit(val issue: Issue) : SearchItem
    data class CodeHit(val file: SearchCode) : SearchItem
}

class SearchViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<List<SearchItem>>?>(null)
    val state: StateFlow<LoadState<List<SearchItem>>?> = _state.asStateFlow()

    private var lastQuery: String = ""
    private var lastScope: SearchScope = SearchScope.REPOS

    fun search(query: String, scope: SearchScope, force: Boolean = false) {
        val q = query.trim()
        if (q.isEmpty()) {
            _state.value = null
            return
        }
        if (!force && q == lastQuery && scope == lastScope && _state.value is LoadState.Ready) return
        lastQuery = q
        lastScope = scope
        viewModelScope.launchLoad({ _state.value = it }) {
            when (scope) {
                SearchScope.REPOS -> GitHubApi.searchRepos(q, page = 1)
                    .value.items.map { SearchItem.RepoHit(it) }

                SearchScope.USERS -> GitHubApi.searchUsers(q, page = 1)
                    .value.items.map { SearchItem.UserHit(it) }

                SearchScope.ISSUES -> GitHubApi.searchIssues(q, page = 1)
                    .value.items.map { SearchItem.IssueHit(it) }

                SearchScope.CODE -> GitHubApi.searchCode(q, page = 1)
                    .value.items.map { SearchItem.CodeHit(it) }
            }
        }
    }

    fun retry() = search(lastQuery, lastScope, force = true)
}
