package io.github.busybin.gitrehub.compose.code

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
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
    /** Ref the file was opened at; commits land on it, or on the default branch when null. */
    val ref: String? = null,
    /** False when [ref] is a tag or commit: it can seed a branch, but nothing can commit onto it. */
    val refIsBranch: Boolean = true,
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

    /** The commit went through; the screen navigates away (with [prNumber] when a PR was opened). */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

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
                    // Images and files above the preview limit have no text to edit: never open the
                    // editor with an empty body, or submitting would wipe the file.
                    val body = entry
                        ?.takeIf { !it.isImage && it.size <= RepoContent.MAX_PREVIEW_BYTES }
                        ?.let { RepoContent.text(it) }
                        ?: error("not an editable text file")
                    val repoInfo = runCatching { GitHubApi.repo(owner, repo) }.getOrNull()
                    val cleanRef = ref?.takeIf { it.isNotBlank() }
                    // A tag or a commit can seed a new branch but cannot receive a commit, so the
                    // screen must not offer a direct commit there.
                    val refIsBranch = cleanRef == null ||
                        runCatching { GitHubApi.branchHeadSha(owner, repo, cleanRef) }.isSuccess
                    EditFileState(
                        loading = false,
                        sha = entry?.sha,
                        text = body,
                        defaultBranch = repoInfo?.defaultBranch?.takeIf { it.isNotBlank() } ?: "main",
                        ref = cleanRef,
                        refIsBranch = refIsBranch,
                        canWrite = repoInfo?.permissions?.canWrite ?: false,
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
        val current = _state.value
        if (newBranch.isNullOrBlank() && current.ref != null && !current.refIsBranch) {
            _error.value = UiText.res(R.string.m3_edit_ref_not_branch)
            return
        }
        _submitting.value = true
        _error.value = null
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val state = _state.value
                    val ref = state.ref
                    val sha = state.sha
                    val encoded = Base64.encodeToString(
                        content.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP,
                    )
                    if (newBranch.isNullOrBlank()) {
                        val branch = ref ?: state.defaultBranch
                        GitHubApi.putFile(
                            owner = owner,
                            repo = repo,
                            path = path,
                            message = message.trim().ifBlank { "Update $path" },
                            content = encoded,
                            sha = sha,
                            branch = branch,
                        )
                        null
                    } else {
                        // A branch starts from the commit the file was opened at: branchHeadSha
                        // only knows heads, so a tag or a commit is resolved through the commits
                        // endpoint instead.
                        val head = if (state.refIsBranch) {
                            val branch = ref ?: state.defaultBranch
                            GitHubApi.branchHeadSha(owner, repo, branch)
                        } else {
                            GitHubApi.commit(owner, repo, ref.orEmpty()).sha
                        }
                        GitHubApi.createBranch(owner, repo, newBranch.trim(), head)
                        // The new branch starts at the same commit, so the file exists there and
                        // its blob sha is the one the file was loaded with.
                        GitHubApi.putFile(
                            owner = owner,
                            repo = repo,
                            path = path,
                            message = message.trim().ifBlank { "Update $path" },
                            content = encoded,
                            sha = sha,
                            branch = newBranch.trim(),
                        )
                        val title = prTitle?.trim()?.takeIf { it.isNotBlank() }
                            ?: "Update ${path.substringAfterLast('/')}"
                        GitHubApi.createPull(
                            owner = owner,
                            repo = repo,
                            title = title,
                            head = newBranch.trim(),
                            // A pull request cannot target a tag, so non-branch refs land on the
                            // default branch.
                            base = if (state.refIsBranch) {
                                ref ?: state.defaultBranch
                            } else {
                                state.defaultBranch
                            },
                            body = prBody?.trim()?.takeIf { it.isNotBlank() },
                        ).number
                    }
                }
            }
            _submitting.value = false
            result.onSuccess {
                _prNumber.value = it
                _finished.value = true
            }.onFailure { _error.value = it.toUiText() }
        }
    }
}
