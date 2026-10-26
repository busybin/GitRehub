package io.github.busybin.gitrehub.compose.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.compose.data.PinnedRepo
import io.github.busybin.gitrehub.compose.data.PinnedRepos
import io.github.busybin.gitrehub.core.model.Gist
import io.github.busybin.gitrehub.core.model.Repo
import io.github.busybin.gitrehub.core.model.User
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Another user's profile page: the account plus the first page of their public repositories. */
data class UserPage(
    val user: User,
    val repos: List<Repo>,
    val following: Boolean = false,
    val isSelf: Boolean = false,
    val isOrg: Boolean = false,
    /** Pinned repositories; empty for organizations (they pin through a different GraphQL root). */
    val pinned: List<PinnedRepo> = emptyList(),
)

/** The four user-page tabs (loaded on demand). */
enum class UserTab(@StringRes val labelRes: Int) {
    REPOS(R.string.m3_profile_repos),
    STARRED(R.string.m3_starred),
    GISTS(R.string.m3_gists),
    FOLLOWING(R.string.m3_following),
    FOLLOWERS(R.string.m3_followers),
    MEMBERS(R.string.m3_members),
}

/**
 * Loads `GET /users/{login}` and that user's repositories.
 *
 * The repositories are best-effort: a profile page that fails entirely because the repo list was
 * rate-limited would be worse than one that shows the profile and no repos.
 */
class UserProfileViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<UserPage>>(LoadState.Loading)
    val state: StateFlow<LoadState<UserPage>> = _state.asStateFlow()

    private val _tab = MutableStateFlow(UserTab.REPOS)
    val tab: StateFlow<UserTab> = _tab.asStateFlow()

    private val _starred = MutableStateFlow<LoadState<List<Repo>>?>(null)
    val starred: StateFlow<LoadState<List<Repo>>?> = _starred.asStateFlow()

    private val _gists = MutableStateFlow<LoadState<List<Gist>>?>(null)
    val gists: StateFlow<LoadState<List<Gist>>?> = _gists.asStateFlow()

    private val _following = MutableStateFlow<LoadState<List<User>>?>(null)
    val following: StateFlow<LoadState<List<User>>?> = _following.asStateFlow()

    private val _followers = MutableStateFlow<LoadState<List<User>>?>(null)
    val followers: StateFlow<LoadState<List<User>>?> = _followers.asStateFlow()

    private val _members = MutableStateFlow<LoadState<List<User>>?>(null)
    val members: StateFlow<LoadState<List<User>>?> = _members.asStateFlow()

    private val _followBusy = MutableStateFlow(false)
    val followBusy: StateFlow<Boolean> = _followBusy.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    private var loadedLogin: String? = null

    /** Switch tab, loading it on first use; `force` retries after a failure. */
    fun selectTab(login: String, tab: UserTab, force: Boolean = false) {
        _tab.value = tab
        when (tab) {
            UserTab.REPOS -> Unit
            UserTab.STARRED -> if (force || _starred.value == null) {
                load(login, _starred) { GitHubApi.starredRepos(login, 1).value }
            }

            UserTab.GISTS -> if (force || _gists.value == null) {
                load(login, _gists) { GitHubApi.userGists(login, 1).value }
            }

            UserTab.FOLLOWING -> if (force || _following.value == null) {
                load(login, _following) { GitHubApi.following(login, 1).value }
            }

            UserTab.FOLLOWERS -> if (force || _followers.value == null) {
                load(login, _followers) { GitHubApi.followers(login, 1).value }
            }

            UserTab.MEMBERS -> if (force || _members.value == null) {
                load(login, _members) { GitHubApi.orgMembers(login, 1).value }
            }
        }
    }

    private fun <T> load(
        login: String,
        target: MutableStateFlow<LoadState<List<T>>?>,
        block: suspend () -> List<T>,
    ) {
        target.value = LoadState.Loading
        viewModelScope.launch {
            val result = runCatching { block() }
            target.value = result.fold(
                onSuccess = { LoadState.Ready(it) },
                onFailure = { LoadState.Failure(it.toUiText(), it) },
            )
        }
    }

    fun currentTab(): UserTab = _tab.value

    fun load(login: String) {
        if (login.isBlank()) return
        if (loadedLogin == login && _state.value is LoadState.Ready) return
        loadedLogin = login
        viewModelScope.launchLoad({ _state.value = it }) {
            val user = GitHubApi.user(login)
            val repos = runCatching { GitHubApi.userRepos(login, 1).value }.getOrDefault(emptyList())
            val me = runCatching { Session.currentAccount()?.login }.getOrNull()
            val isSelf = me != null && me.equals(login, ignoreCase = true)
            val following = !isSelf && runCatching { GitHubApi.isFollowing(login) }
                .getOrDefault(false)
            val isOrg = user.type.equals("Organization", ignoreCase = true)
            val pinned = if (isOrg) emptyList() else {
                runCatching { PinnedRepos.load(login) }.getOrDefault(emptyList())
            }
            UserPage(
                user = user,
                repos = repos,
                following = following,
                isSelf = isSelf,
                isOrg = isOrg,
                pinned = pinned,
            )
        }
    }

    /** Follow / unfollow toggles both ways; the follower count moves with it. */
    fun toggleFollow() {
        val page = (_state.value as? LoadState.Ready)?.value ?: return
        if (page.isSelf || _followBusy.value) return
        val targetLogin = page.user.login.ifBlank { loadedLogin ?: return }
        _followBusy.value = true
        viewModelScope.launch {
            val target = !page.following
            val ok = runCatching { GitHubApi.setFollowing(targetLogin, target) }.isSuccess
            if (ok) {
                val user = page.user.copy(
                    followers = (page.user.followers + if (target) 1 else -1).coerceAtLeast(0),
                )
                _state.value = LoadState.Ready(page.copy(user = user, following = target))
            } else {
                _message.value = UiText.res(R.string.m3_action_failed)
            }
            _followBusy.value = false
        }
    }
}
