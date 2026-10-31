package io.github.busybin.gitrehub.compose.releases

import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.PagedViewModel
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.model.Release
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.hasMore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Releases / tags of a repository.
 *
 * Directly answers the most repeated complaint about the official mobile app: users can not even
 * *see* the release section of a repository there. Write access additionally unlocks the release
 * workbench (create / edit / delete, asset removal).
 */
class ReleasesViewModel : PagedViewModel<Release>() {

    private var owner: String = ""
    private var repo: String = ""

    private val _canWrite = MutableStateFlow(false)
    val canWrite: StateFlow<Boolean> = _canWrite.asStateFlow()

    private val _defaultBranch = MutableStateFlow("main")
    val defaultBranch: StateFlow<String> = _defaultBranch.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun setup(owner: String, repo: String) {
        if (this.owner == owner && this.repo == repo) return
        this.owner = owner
        this.repo = repo
        refresh()
        viewModelScope.launch {
            val info = runCatching {
                withContext(Dispatchers.IO) { GitHubApi.repo(owner, repo) }
            }.getOrNull() ?: return@launch
            _canWrite.value = info.permissions?.canWrite == true
            _defaultBranch.value = info.defaultBranch ?: "main"
        }
    }

    override suspend fun fetch(page: Int): PageResult<Release> {
        if (owner.isBlank() || repo.isBlank()) return PageResult(emptyList(), false)
        val response = GitHubApi.releases(owner, repo, page)
        return PageResult(response.value, response.hasMore())
    }

    /** Delete one of the uploaded files of a release. */
    fun deleteAsset(owner: String, repo: String, assetId: Long) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { GitHubApi.deleteReleaseAsset(owner, repo, assetId) }
            }
            _busy.value = false
            result.onSuccess {
                _message.value = UiText.res(R.string.m3_release_asset_deleted)
                refresh()
            }.onFailure { _message.value = it.toUiText() }
        }
    }
}
