package io.github.busybin.gitrehub.compose.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.CheckRun
import io.github.busybin.gitrehub.core.model.CommitFile
import io.github.busybin.gitrehub.core.model.PullRequest
import io.github.busybin.gitrehub.core.model.RepoPermissions
import io.github.busybin.gitrehub.core.model.Review
import io.github.busybin.gitrehub.core.model.ReviewComment
import io.github.busybin.gitrehub.core.net.ApiException
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.net.WriteBodies
import io.github.busybin.gitrehub.core.net.hasMore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An inline comment written on the screen but not submitted yet (GitHub's "pending review"). */
data class DraftComment(
    val path: String,
    val line: Int,
    val side: String,
    val body: String,
)

data class PullRequestFilesData(
    val pullRequest: PullRequest,
    val files: List<CommitFile>,
    val reviews: List<Review>,
    val comments: List<ReviewComment>,
    val checks: List<CheckRun>,
    /** True when the repository has more files than the capped page loop fetched. */
    val truncated: Boolean,
    val myLogin: String?,
    /** What the signed-in account may do here; null when the repo lookup failed. */
    val permissions: RepoPermissions? = null,
)

/** One-shot result shown above the list (green on success, red on failure). */
data class FilesMessage(val text: UiText, val error: Boolean)

/**
 * The pull request's "Files changed" screen: the aggregate diff, the reviews so far, the checks of
 * the head commit, plus writing a review (inline comments, then approve / request changes /
 * comment in one submission).
 */
class PullRequestFilesViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<PullRequestFilesData>>(LoadState.Loading)
    val state: StateFlow<LoadState<PullRequestFilesData>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<FilesMessage?>(null)
    val message: StateFlow<FilesMessage?> = _message.asStateFlow()

    private val _draft = MutableStateFlow<List<DraftComment>>(emptyList())
    val draft: StateFlow<List<DraftComment>> = _draft.asStateFlow()

    private var key: String = ""

    fun load(owner: String, repo: String, number: Int, force: Boolean = false) {
        val newKey = "$owner/$repo#$number"
        if (!force && key == newKey && _state.value is LoadState.Ready) return
        key = newKey
        viewModelScope.launchLoad({ _state.value = it }) { fetch(owner, repo, number) }
    }

    private suspend fun fetch(owner: String, repo: String, number: Int): PullRequestFilesData {
        val pull = GitHubApi.pullRequest(owner, repo, number)

        val files = ArrayList<CommitFile>()
        var page = 1
        var truncated = false
        while (page <= MAX_FILE_PAGES) {
            val result = GitHubApi.pullRequestFiles(owner, repo, number, page)
            files += result.value
            if (!result.hasMore(pageSize = 100)) break
            if (page == MAX_FILE_PAGES) truncated = true
            page++
        }

        val reviews = runCatching { GitHubApi.pullRequestReviews(owner, repo, number) }
            .getOrDefault(emptyList())
        val comments = runCatching { GitHubApi.pullRequestReviewComments(owner, repo, number) }
            .getOrDefault(emptyList())
        val checks = pull.head?.sha?.let { sha ->
            runCatching { GitHubApi.checkRuns(owner, repo, sha) }.getOrNull()
        }.orEmpty()
        val myLogin = runCatching { Session.currentAccount()?.login }.getOrNull()
        // Reviewing needs write access; a read-only visitor must not see live review controls.
        val permissions = runCatching { GitHubApi.repo(owner, repo).permissions }.getOrNull()

        return PullRequestFilesData(
            pullRequest = pull,
            files = files,
            reviews = reviews,
            comments = comments,
            checks = checks,
            truncated = truncated,
            myLogin = myLogin,
            permissions = permissions,
        )
    }

    // ------------------------------------------------------------------ pending review

    fun addDraft(path: String, line: Int, side: String, body: String) {
        if (body.isBlank()) return
        _draft.value = _draft.value + DraftComment(path, line, side, body)
    }

    fun removeDraft(index: Int) {
        _draft.value = _draft.value.filterIndexed { i, _ -> i != index }
    }

    fun clearDraft() {
        _draft.value = emptyList()
    }

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * Submits the pending review. [event] is GitHub's verbatim value (`APPROVE`,
     * `REQUEST_CHANGES`, `COMMENT`); a wrong one gets a 422, so the UI only offers these.
     */
    fun submitReview(owner: String, repo: String, number: Int, event: String, body: String) {
        val files = (_state.value as? LoadState.Ready)?.value ?: return
        if (files.permissions?.canWrite != true) {
            _message.value = FilesMessage(UiText.res(R.string.m3_pr_review_no_permission), true)
            return
        }
        val comments = _draft.value.map { draft ->
            WriteBodies.reviewComment(draft.path, draft.line, draft.side, draft.body)
        }
        if (comments.isEmpty() && body.isBlank() && event != "APPROVE") {
            _message.value = FilesMessage(UiText.res(R.string.m3_pr_review_needs_text), true)
            return
        }
        write(owner, repo, number) {
            GitHubApi.submitReview(
                owner = owner,
                repo = repo,
                number = number,
                commitId = files.pullRequest.head?.sha,
                body = body.ifBlank { null },
                event = event,
                comments = comments,
            )
            _draft.value = emptyList()
            UiText.res(R.string.m3_pr_review_done)
        }
    }

    private fun write(
        owner: String,
        repo: String,
        number: Int,
        block: suspend () -> UiText,
    ) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val message = try {
                FilesMessage(withContext(Dispatchers.IO) { block() }, false)
            } catch (t: Throwable) {
                FilesMessage(reviewError(t), true)
            }
            _busy.value = false
            _message.value = message
            load(owner, repo, number, force = true)
        }
    }

    /** GitHub answers a rejected review with a specific message; keep it, it explains itself. */
    private fun reviewError(t: Throwable): UiText {
        if (t is ApiException && t.error.message.isNotBlank()) return UiText.raw(t.error.message)
        return t.toUiText()
    }

    private companion object {
        const val MAX_FILE_PAGES = 3
    }
}
