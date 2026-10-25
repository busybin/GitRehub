package io.github.busybin.gitrehub.compose.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.compose.data.LanguageSlice
import io.github.busybin.gitrehub.compose.data.RepoStats
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Repo
import io.github.busybin.gitrehub.core.model.User
import io.github.busybin.gitrehub.core.model.WatchLevel
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RepoDetail(
    val repo: Repo,
    val readmeHtml: String?,
    val starring: Boolean,
    val watching: Boolean = false,
    /** Watching with every notification blocked ("ignore"). */
    val watchIgnored: Boolean = false,
    val languages: List<LanguageSlice> = emptyList(),
    val contributors: List<User> = emptyList(),
    val counts: RepoStats.RepoCounts = RepoStats.RepoCounts(),
)

class RepoDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<RepoDetail>>(LoadState.Loading)
    val state: StateFlow<LoadState<RepoDetail>> = _state.asStateFlow()

    private val _starBusy = MutableStateFlow(false)
    val starBusy: StateFlow<Boolean> = _starBusy.asStateFlow()

    private var owner: String = ""
    private var name: String = ""

    /** One-shot message (star / fork success or failure); the Compose layer clears it after consuming. */
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun load(owner: String, name: String, force: Boolean = false) {
        if (!force && this.owner == owner && this.name == name && _state.value is LoadState.Ready) return
        this.owner = owner
        this.name = name
        // Reset per-repo action state, otherwise the previous repo's "forked"/"watching" leaks in.
        _forked.value = false
        _forkBusy.value = false
        _forkOwned.value = false
        _forkFullName.value = null
        _watchBusy.value = false
        viewModelScope.launchLoad({ _state.value = it }) { fetch() }
    }

    private suspend fun fetch(): RepoDetail {
        val repo = GitHubApi.repo(owner, name)
        return coroutineScope {
            // Fire all requests in parallel; the detail screen is slow enough already.
            val readmeDeferred = async {
                runCatching { GitHubApi.readmeHtml(owner, name) }.getOrNull()
            }
            val starringDeferred = async {
                runCatching { GitHubApi.isStarred(owner, name) }.getOrDefault(false)
            }
            // No subscription resource (404) means "not watching"; any failure reads the same way.
            val subscriptionDeferred = async {
                runCatching { GitHubApi.repoSubscription(owner, name) }.getOrNull()
            }
            val languagesDeferred = async {
                runCatching { RepoStats.languages(owner, name) }.getOrDefault(emptyList())
            }
            val contributorsDeferred = async {
                runCatching { GitHubApi.contributors(owner, name, 1).value }.getOrDefault(emptyList())
            }
            val countsDeferred = async {
                runCatching { RepoStats.counts(owner, name, repo.openIssuesCount) }
                    .getOrDefault(RepoStats.RepoCounts())
            }
            // One's own repos cannot be forked again, so the button is greyed out instead of toggled.
            val currentLogin = runCatching { Session.currentAccount()?.login }.getOrNull()
            _forkOwned.value = currentLogin != null && repo.owner?.login == currentLogin
            val subscription = subscriptionDeferred.await()
            RepoDetail(
                repo = repo,
                readmeHtml = readmeDeferred.await(),
                starring = starringDeferred.await(),
                // `ignored` alone still means "subscribed but muted", which the button must show.
                watching = subscription != null && (subscription.subscribed || subscription.ignored),
                watchIgnored = subscription?.ignored == true,
                languages = languagesDeferred.await(),
                contributors = contributorsDeferred.await(),
                counts = countsDeferred.await(),
            )
        }
    }

    fun toggleStar() {
        val current = (_state.value as? LoadState.Ready)?.value ?: return
        if (_starBusy.value) return
        _starBusy.value = true
        viewModelScope.launch {
            val target = !current.starring
            val ok = runCatching { GitHubApi.setStarred(owner, name, target) }.isSuccess
            if (ok) {
                _state.value = LoadState.Ready(current.copy(starring = target))
            } else {
                _message.value = UiText.res(R.string.m3_action_failed)
            }
            _starBusy.value = false
        }
    }

    private val _forkBusy = MutableStateFlow(false)
    val forkBusy: StateFlow<Boolean> = _forkBusy.asStateFlow()

    /** The repository belongs to the current account, so forking is not possible (grey button). */
    private val _forkOwned = MutableStateFlow(false)
    val forkOwned: StateFlow<Boolean> = _forkOwned.asStateFlow()

    /** A fork was created in this session; the button turns blue and opens it on the next tap. */
    private val _forked = MutableStateFlow(false)
    val forked: StateFlow<Boolean> = _forked.asStateFlow()

    private val _forkFullName = MutableStateFlow<String?>(null)
    val forkFullName: StateFlow<String?> = _forkFullName.asStateFlow()

    private val _watchBusy = MutableStateFlow(false)
    val watchBusy: StateFlow<Boolean> = _watchBusy.asStateFlow()

    /** Fork, with an optional new name and "default branch only" (GitHub's `default_branch_only`). */
    fun forkRepo(forkName: String, defaultBranchOnly: Boolean) {
        if (_forkBusy.value) return
        _forkBusy.value = true
        viewModelScope.launch {
            val newName = forkName.trim().takeIf { it.isNotEmpty() && it != name }
            val result = runCatching {
                GitHubApi.forkRepo(owner, name, newName, defaultBranchOnly)
            }.getOrNull()
            if (result != null) {
                _forked.value = true
                val login = runCatching { Session.currentAccount()?.login }.getOrNull()
                _forkFullName.value = result.fullName.takeIf { it.contains('/') }
                    ?: listOfNotNull(login, result.name.ifBlank { forkName }).joinToString("/")
                _message.value = UiText.res(R.string.m3_fork_started)
            } else {
                _message.value = UiText.res(R.string.m3_action_failed)
            }
            _forkBusy.value = false
        }
    }

    /**
     * Set the subscription: [WatchLevel.ALL] for everything, [WatchLevel.IGNORED] for "block all
     * notifications", `null` to unwatch. The button follows the returned state.
     */
    fun setWatch(level: WatchLevel?) {
        val current = (_state.value as? LoadState.Ready)?.value ?: return
        if (_watchBusy.value) return
        _watchBusy.value = true
        viewModelScope.launch {
            val ok = runCatching { GitHubApi.setWatchLevel(owner, name, level) }.isSuccess
            if (ok) {
                _state.value = LoadState.Ready(
                    current.copy(
                        watching = level != null,
                        watchIgnored = level == WatchLevel.IGNORED,
                    )
                )
            } else {
                _message.value = UiText.res(R.string.m3_action_failed)
            }
            _watchBusy.value = false
        }
    }
}
