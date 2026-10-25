package io.github.busybin.gitrehub.compose.gists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Gist
import io.github.busybin.gitrehub.core.model.GistFile
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.GitHubClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One gist. The list response already has every file, so this only re-reads when opened from a
 * link; files GitHub truncated get their full text from the raw URL on demand.
 */
class GistDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<Gist>>(LoadState.Loading)
    val state: StateFlow<LoadState<Gist>> = _state.asStateFlow()

    /** Full text of files larger than the API inlines, keyed by filename. */
    private val _full = MutableStateFlow<Map<String, String>>(emptyMap())
    val full: StateFlow<Map<String, String>> = _full.asStateFlow()

    private val _loadingFull = MutableStateFlow<Set<String>>(emptySet())
    val loadingFull: StateFlow<Set<String>> = _loadingFull.asStateFlow()

    /** The gist was deleted on GitHub; the screen pops itself. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    /** Login of the signed-in account, so the delete action only shows on own gists. */
    private val _myLogin = MutableStateFlow<String?>(null)
    val myLogin: StateFlow<String?> = _myLogin.asStateFlow()

    fun consumeMessage() { _message.value = null }

    private var id: String = ""
    private var loaded = false
    private var deleting = false

    fun load(id: String, force: Boolean = false) {
        if (id.isBlank()) return
        if (!force && id == this.id && loaded && _state.value is LoadState.Ready) return
        this.id = id
        loaded = true
        _full.value = emptyMap()
        viewModelScope.launch {
            _myLogin.value = runCatching { Session.currentAccount()?.login }.getOrNull()
        }
        viewModelScope.launchLoad({ _state.value = it }) { GitHubApi.gist(id) }
    }

    fun loadFull(file: GistFile) {
        val url = file.rawUrl ?: return
        val name = file.filename
        if (_full.value.containsKey(name) || name in _loadingFull.value) return
        _loadingFull.value = _loadingFull.value + name
        viewModelScope.launch {
            val result = runCatching { GitHubClient.getTextAbsolute(url) }
            result.onSuccess { text -> _full.value = _full.value + (name to text) }
            result.onFailure { _message.value = it.toUiText() }
            _loadingFull.value = _loadingFull.value - name
        }
    }

    fun delete() {
        if (deleting || id.isBlank()) return
        deleting = true
        viewModelScope.launch {
            val result = runCatching { GitHubApi.deleteGist(id) }
            result.onSuccess { _deleted.value = true }
            result.onFailure { _message.value = UiText.res(R.string.m3_gist_delete_failed) }
            deleting = false
        }
    }
}
