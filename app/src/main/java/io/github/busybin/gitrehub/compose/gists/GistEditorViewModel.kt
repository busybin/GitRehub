package io.github.busybin.gitrehub.compose.gists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.model.Gist
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GistEditorData(
    val gist: Gist? = null,
    val filename: String = "gistfile1.md",
    val content: String = "",
    val description: String = "",
    val public: Boolean = false,
)

/** Create a gist, or edit one of the account's own gists (same file name). */
class GistEditorViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<GistEditorData>>(LoadState.Loading)
    val state: StateFlow<LoadState<GistEditorData>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun load(id: String?, initialContent: String?) {
        viewModelScope.launchLoad({ _state.value = it }) {
            if (id.isNullOrBlank()) {
                GistEditorData(content = initialContent.orEmpty())
            } else {
                val gist = GitHubApi.gist(id)
                val file = gist.filesSorted.firstOrNull()
                GistEditorData(
                    gist = gist,
                    filename = file?.filename ?: "gistfile1.md",
                    content = file?.content.orEmpty(),
                    description = gist.description.orEmpty(),
                    public = gist.public,
                )
            }
        }
    }

    fun save(id: String?, description: String, public: Boolean, filename: String, content: String) {
        if (_busy.value) return
        val name = filename.trim().ifBlank { "gistfile1.md" }
        if (content.isBlank()) {
            _message.value = UiText.res(R.string.m3_gist_empty)
            return
        }
        _busy.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (id.isNullOrBlank()) {
                        GitHubApi.createGist(description, public, mapOf(name to content))
                    } else {
                        GitHubApi.updateGist(id, description, mapOf(name to content))
                    }
                }
            }
            _busy.value = false
            result.onSuccess {
                _message.value = UiText.res(R.string.m3_gist_saved)
                _saved.value = true
            }.onFailure { _message.value = it.toUiText() }
        }
    }
}
