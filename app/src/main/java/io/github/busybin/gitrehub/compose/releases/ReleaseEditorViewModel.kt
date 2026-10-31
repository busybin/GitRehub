package io.github.busybin.gitrehub.compose.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.model.GeneratedNotes
import io.github.busybin.gitrehub.core.model.Release
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the editor needs before it can be filled in. */
data class ReleaseEditorData(
    val release: Release? = null,
    val defaultBranch: String = "main",
    val canWrite: Boolean = false,
    /** Existing tags, newest first, for the "previous tag" picker. */
    val tags: List<String> = emptyList(),
)

/**
 * Create / edit a release, and generate its notes from merged pull requests.
 *
 * GitHub's `generate-notes` takes the tag being released plus the previous tag; the UI offers the
 * repository's releases (newest first) as that previous tag, which is the same thing the web UI does
 * behind "Generate release notes".
 */
class ReleaseEditorViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<ReleaseEditorData>>(LoadState.Loading)
    val state: StateFlow<LoadState<ReleaseEditorData>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    private val _generated = MutableStateFlow<GeneratedNotes?>(null)
    val generated: StateFlow<GeneratedNotes?> = _generated.asStateFlow()

    private val _savedId = MutableStateFlow<Long?>(null)
    val savedId: StateFlow<Long?> = _savedId.asStateFlow()

    private var key = ""

    fun load(owner: String, repo: String, releaseId: Long?) {
        val newKey = "$owner/$repo/${releaseId ?: 0}"
        if (key == newKey && _state.value is LoadState.Ready) return
        key = newKey
        _generated.value = null
        _savedId.value = null
        viewModelScope.launchLoad({ _state.value = it }) {
            val info = runCatching { GitHubApi.repo(owner, repo) }.getOrNull()
            val release = releaseId?.let { id ->
                GitHubApi.release(owner, repo, id)
            }
            val releases = runCatching { GitHubApi.releases(owner, repo, 1).value }
                .getOrDefault(emptyList())
            ReleaseEditorData(
                release = release,
                defaultBranch = release?.targetCommitish?.takeIf { it.isNotBlank() }
                    ?: info?.defaultBranch ?: "main",
                canWrite = info?.permissions?.canWrite ?: true,
                tags = releases.mapNotNull { it.tagName },
            )
        }
    }

    fun consumeMessage() { _message.value = null }
    fun consumeGenerated() { _generated.value = null }

    /** Generate notes for [tag], optionally limited to what came after [previousTag]. */
    fun generate(owner: String, repo: String, tag: String, target: String?, previousTag: String?) {
        if (tag.isBlank() || _busy.value) {
            if (tag.isBlank()) _message.value = UiText.res(R.string.m3_release_tag_required)
            return
        }
        _busy.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    GitHubApi.generateReleaseNotes(owner, repo, tag.trim(), target, previousTag)
                }
            }
            _busy.value = false
            result.onSuccess { _generated.value = it }
                .onFailure { _message.value = it.toUiText() }
        }
    }

    fun save(
        owner: String,
        repo: String,
        existing: Release?,
        tag: String,
        target: String,
        name: String,
        body: String,
        draft: Boolean,
        prerelease: Boolean,
    ) {
        if (tag.isBlank() || _busy.value) {
            if (tag.isBlank()) _message.value = UiText.res(R.string.m3_release_tag_required)
            return
        }
        _busy.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (existing == null) {
                        GitHubApi.createRelease(
                            owner, repo, tag.trim(), target.trim().ifBlank { null },
                            name.trim(), body, draft, prerelease,
                        )
                    } else {
                        GitHubApi.updateRelease(
                            owner, repo, existing.id,
                            // A published release cannot be retagged; only send it while it is a draft.
                            tagName = if (existing.draft && tag.trim() != existing.tagName) tag.trim() else null,
                            targetCommitish = target.trim().ifBlank { null },
                            name = name.trim(),
                            body = body,
                            draft = draft,
                            prerelease = prerelease,
                        )
                    }
                }
            }
            _busy.value = false
            result.onSuccess {
                _message.value = UiText.res(R.string.m3_release_saved)
                _savedId.value = it.id
            }.onFailure { _message.value = it.toUiText() }
        }
    }

    fun delete(owner: String, repo: String, release: Release) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { GitHubApi.deleteRelease(owner, repo, release.id) }
            }
            _busy.value = false
            result.onSuccess {
                _message.value = UiText.res(R.string.m3_release_deleted)
                _savedId.value = -1L
            }.onFailure { _message.value = it.toUiText() }
        }
    }
}
