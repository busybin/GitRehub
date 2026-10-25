package io.github.busybin.gitrehub.compose.code

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.compose.data.RepoContent
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditFileState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val sha: String? = null,
    val text: String = "",
    val defaultBranch: String = "main",
    val canWrite: Boolean = false,
)

/** Edits one text file, committing straight to the default branch or on a new branch plus a PR. */
class EditFileViewModel : ViewModel() {

    private val _state = MutableStateFlow(EditFileState())
    val state: StateFlow<EditFileState> = _state.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()

    /** Number of the pull request created on submit, when one was created. */
    private val _prNumber = MutableStateFlow<Int?>(null)
    val prNumber: StateFlow<Int?> = _prNumber.asStateFlow()

    private var loadedKey = ""

    fun load(owner: String, repo: String, path: String, ref: String?, force: Boolean = false) {
        val key = "$owner/$repo/$path@$ref"
        if (!force && key == loadedKey && !_state.value.failed) return
        loadedKey = key
        _state.value = _state.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    val entry = RepoContent.file(owner, repo, path, ref)
                    val repoInfo = runCatching { GitHubApi.repo(owner, repo) }.getOrNull()
                    EditFileState(
                        loading = false,
                        sha = entry?.sha,
                        text = entry?.let { RepoContent.decode(it) }.orEmpty(),
                        defaultBranch = repoInfo?.defaultBranch?.takeIf { it.isNotBlank() } ?: "main",
                        canWrite = repoInfo?.permissions?.canWrite ?: true,
                    )
                }
            }
            _state.value = loaded.getOrElse { _state.value.copy(loading = false, failed = true) }
        }
    }

    /**
     * Commits [content]. [newBranch] null means "commit to the default branch"; otherwise the branch
     * is created from the default branch, the file is committed there and a PR is opened.
     */
    fun submit(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        newBranch: String?,
        prTitle: String?,
        prBody: String?,
    ) {
        if (_submitting.value) return
        _submitting.value = true
        _error.value = null
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val base = _state.value.defaultBranch
                    val encoded = Base64.encodeToString(
                        content.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP,
                    )
                    if (newBranch.isNullOrBlank()) {
                        GitHubApi.putFile(
                            owner = owner,
                            repo = repo,
                            path = path,
                            message = message.trim().ifBlank { "Update $path" },
                            content = encoded,
                            sha = _state.value.sha,
                            branch = base,
                        )
                        null
                    } else {
                        val head = GitHubApi.branchHeadSha(owner, repo, base)
                        GitHubApi.createBranch(owner, repo, newBranch.trim(), head)
                        GitHubApi.putFile(
                            owner = owner,
                            repo = repo,
                            path = path,
                            message = message.trim().ifBlank { "Update $path" },
                            content = encoded,
                            branch = newBranch.trim(),
                        )
                        val title = prTitle?.trim()?.takeIf { it.isNotBlank() }
                            ?: "Update ${path.substringAfterLast('/')}"
                        GitHubApi.createPull(
                            owner = owner,
                            repo = repo,
                            title = title,
                            head = newBranch.trim(),
                            base = base,
                            body = prBody?.trim()?.takeIf { it.isNotBlank() },
                        ).number
                    }
                }
            }
            _submitting.value = false
            result.onSuccess { _prNumber.value = it }
                .onFailure { _error.value = it.toUiText() }
        }
    }
}
