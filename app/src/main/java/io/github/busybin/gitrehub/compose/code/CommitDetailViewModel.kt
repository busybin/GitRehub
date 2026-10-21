package io.github.busybin.gitrehub.compose.code

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.core.model.Commit
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One commit's detail: `GET /repos/{o}/{r}/commits/{sha}`.
 *
 * The response's `files[]` carries `patch` (the diff hunk), so there is no need for a separate diff
 * call. GitHub only omits `patch` when there are more than 300 files or it is too large, and then the
 * UI points at the browser.
 */
class CommitDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<Commit>>(LoadState.Loading)
    val state: StateFlow<LoadState<Commit>> = _state.asStateFlow()

    private var key: String = ""

    fun load(owner: String, repo: String, sha: String, force: Boolean = false) {
        val newKey = "$owner/$repo@$sha"
        if (!force && key == newKey && _state.value is LoadState.Ready) return
        key = newKey
        viewModelScope.launchLoad({ _state.value = it }) { GitHubApi.commit(owner, repo, sha) }
    }
}
