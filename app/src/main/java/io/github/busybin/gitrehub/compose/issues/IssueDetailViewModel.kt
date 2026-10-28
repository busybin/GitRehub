package io.github.busybin.gitrehub.compose.issues

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.DraftQueue
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.model.Comment
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.model.Label
import io.github.busybin.gitrehub.core.model.Milestone
import io.github.busybin.gitrehub.core.model.PullRequest
import io.github.busybin.gitrehub.core.model.RepoPermissions
import io.github.busybin.gitrehub.core.model.User
import io.github.busybin.gitrehub.core.net.ApiException
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.core.model.Commit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Values must match GitHub's `merge_method` verbatim (a wrong value gets 422). */
enum class MergeMethod(val api: String, @StringRes val labelRes: Int) {
    MERGE("merge", R.string.m3_write_merge_commit),
    SQUASH("squash", R.string.m3_write_squash),
    REBASE("rebase", R.string.m3_write_rebase),
}

/** Eight reactions; `api` is GitHub's `content` value (a wrong value gets 422), emoji is display only. */
enum class ReactionKind(val api: String, val emoji: String, @StringRes val labelRes: Int) {
    PLUS_ONE("+1", "👍", R.string.m3_reaction_plus_one),
    MINUS_ONE("-1", "👎", R.string.m3_reaction_minus_one),
    LAUGH("laugh", "😄", R.string.m3_reaction_laugh),
    HOORAY("hooray", "🎉", R.string.m3_reaction_hooray),
    CONFUSED("confused", "😕", R.string.m3_reaction_confused),
    HEART("heart", "❤️", R.string.m3_reaction_heart),
    ROCKET("rocket", "🚀", R.string.m3_reaction_rocket),
    EYES("eyes", "👀", R.string.m3_reaction_eyes);

    companion object {
        fun of(content: String?): ReactionKind? = entries.firstOrNull { it.api == content }
    }
}

/** Where a reaction is attached: the issue itself (incl. PR) or one comment. */
sealed interface ReactionTarget {
    data class IssueLevel(val number: Int) : ReactionTarget
    data class CommentLevel(val commentId: Long) : ReactionTarget
}

/** One-shot write result shown to the user (green on success, red on failure). */
data class WriteMessage(val text: UiText, val error: Boolean)

data class IssueDetailData(
    val issue: Issue,
    val pullRequest: PullRequest?,
    val comments: List<Comment>,
    val commits: List<Commit> = emptyList(),
    /** What the signed-in account may do here; null when the repo lookup failed. */
    val permissions: RepoPermissions? = null,
)

/** Candidates for the manage drawer: all repo labels, assignable users and milestones. */
data class ManageOptions(
    val labels: List<Label> = emptyList(),
    val assignees: List<User> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Issue **and** pull-request detail. GitHub's issues endpoints return pull requests too, so one
 * screen covers both; for PRs we additionally fetch the rich PR object (merge state, diff stats).
 *
 * All writes go through [write]: mark busy, run, report one message, then silently reload.
 */
class IssueDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<IssueDetailData>>(LoadState.Loading)
    val state: StateFlow<LoadState<IssueDetailData>> = _state.asStateFlow()

    /** True while a write is in flight; the UI greys buttons and shows a thin progress bar. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<WriteMessage?>(null)
    val message: StateFlow<WriteMessage?> = _message.asStateFlow()

    /** Current login; decides whether edit/delete is offered and whether a reaction is mine. */
    private val _myLogin = MutableStateFlow<String?>(null)
    val myLogin: StateFlow<String?> = _myLogin.asStateFlow()

    /**
     * Reactions tapped in this session (keys like `issue:heart` / `comment:123:heart`).
     * GitHub's summary does not say whether I reacted, so this is local and resets on re-entry.
     */
    private val _myReactions = MutableStateFlow<Set<String>>(emptySet())
    val myReactions: StateFlow<Set<String>> = _myReactions.asStateFlow()

    private val _options = MutableStateFlow(ManageOptions())
    val options: StateFlow<ManageOptions> = _options.asStateFlow()

    private var key: String = ""
    private var optionsKey: String = ""

    fun load(owner: String, repo: String, number: Int, force: Boolean = false) {
        val newKey = "$owner/$repo#$number"
        if (!force && key == newKey && _state.value is LoadState.Ready) return
        key = newKey
        viewModelScope.launchLoad({ _state.value = it }) { fetch(owner, repo, number) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Fetch manage candidates lazily, only when the drawer opens, to avoid three extra requests. */
    fun loadOptions(owner: String, repo: String, force: Boolean = false) {
        val newKey = "$owner/$repo"
        if (!force && optionsKey == newKey && (_options.value.loading || !_options.value.failed)) return
        optionsKey = newKey
        viewModelScope.launch {
            _options.value = ManageOptions(loading = true)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val labels = GitHubApi.repoLabels(owner, repo)
                    val assignees = GitHubApi.assignableUsers(owner, repo)
                    val milestones = runCatching { GitHubApi.repoMilestones(owner, repo) }
                        .getOrDefault(emptyList())
                    ManageOptions(labels, assignees, milestones)
                }
            }
            _options.value = result.getOrElse { ManageOptions(failed = true) }
        }
    }

    /** Silent reload after a successful write: skips LoadState.Loading so the screen does not flash. */
    private fun refresh(owner: String, repo: String, number: Int) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { fetch(owner, repo, number) } }
                .onSuccess { _state.value = LoadState.Ready(it) }
        }
    }

    /**
     * Post a comment (issues and PRs share the same endpoint). Answers that prove the comment was
     * never created (no network, auth, rate limit, server error) keep the text in the offline
     * queue; a validation error is reported and not queued so it does not retry forever.
     */
    fun postComment(owner: String, repo: String, number: Int, body: String) {
        val text = body.trim()
        if (text.isEmpty() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { GitHubApi.createComment(owner, repo, number, text) }
            }
            _busy.value = false
            result.onSuccess {
                _message.value = WriteMessage(UiText.res(R.string.m3_write_comment_done), error = false)
                refresh(owner, repo, number)
            }.onFailure {
                val status = (it as? ApiException)?.error?.status ?: 0
                // Keep the text for every answer that did not create the comment. A 403 is
                // usually a locked thread: keep it too, but mark it so it is not retried silently.
                if (status == 0 || status == 401 || status == 403 || status == 429 || status >= 500) {
                    DraftQueue.add(owner, repo, number, text, blocked = status == 403)
                    _message.value = WriteMessage(
                        if (status == 403) {
                            UiText.res(R.string.m3_draft_not_sent)
                        } else {
                            UiText.res(R.string.m3_draft_saved)
                        },
                        error = true,
                    )
                } else {
                    _message.value = WriteMessage(writeError(it), error = true)
                }
            }
        }
    }

    /** Edit my own comment (`PATCH /issues/comments/{id}`). */
    fun editComment(
        owner: String,
        repo: String,
        number: Int,
        commentId: Long,
        body: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        val text = body.trim()
        if (text.isEmpty()) return
        write(owner, repo, number, UiText.res(R.string.m3_write_comment_edited), onSuccess) {
            GitHubApi.editComment(owner, repo, commentId, text)
        }
    }

    /** Delete my own comment (`DELETE /issues/comments/{id}`). */
    fun deleteComment(owner: String, repo: String, number: Int, commentId: Long) {
        write(owner, repo, number, UiText.res(R.string.m3_write_comment_deleted)) {
            GitHubApi.deleteComment(owner, repo, commentId)
        }
    }

    /** Edit title and body (PRs also go through `/issues/{n}`). */
    fun editIssue(
        owner: String,
        repo: String,
        number: Int,
        title: String,
        body: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        val newTitle = title.trim()
        if (newTitle.isEmpty()) return
        write(owner, repo, number, UiText.res(R.string.m3_write_edited_done), onSuccess) {
            GitHubApi.editIssue(owner, repo, number, newTitle, body)
        }
    }

    /** Manage drawer save: compare the selection with current values and send only the requests that differ. */
    fun applyManagement(
        owner: String,
        repo: String,
        number: Int,
        current: Issue,
        labels: List<String>,
        assignees: List<String>,
        milestoneNumber: Int?,
        milestoneChanged: Boolean,
        locked: Boolean,
    ) {
        val currentLabels = current.labels.mapNotNull { it.name }
        val currentAssignees = current.assignees.mapNotNull { it.login }
            .ifEmpty { listOfNotNull(current.assignee?.login) }

        write(owner, repo, number, UiText.res(R.string.m3_write_updated_done)) {
            if (currentLabels.toSet() != labels.toSet()) {
                GitHubApi.setIssueLabels(owner, repo, number, labels)
            }
            val add = assignees - currentAssignees.toSet()
            val remove = currentAssignees - assignees.toSet()
            if (add.isNotEmpty()) GitHubApi.addAssignees(owner, repo, number, add.toList())
            if (remove.isNotEmpty()) GitHubApi.removeAssignees(owner, repo, number, remove.toList())
            if (milestoneChanged) GitHubApi.setIssueMilestone(owner, repo, number, milestoneNumber)
            if (locked != current.locked) GitHubApi.lockIssue(owner, repo, number, locked)
        }
    }

    /**
     * Close / reopen. GitHub treats PRs as issues, so both use `PATCH /issues/{n}` with a state-only
     * body.
     */
    fun toggleState(owner: String, repo: String, number: Int, closed: Boolean) {
        val done = if (closed) R.string.m3_write_reopened_done else R.string.m3_write_closed_done
        write(owner, repo, number, UiText.res(done)) {
            GitHubApi.setIssueState(owner, repo, number, closed = !closed)
        }
    }

    /** Merge a PR (irreversible, so the UI confirms first and picks a method). */
    fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        method: MergeMethod,
        commitMessage: String?,
    ) {
        write(owner, repo, number, UiText.res(R.string.m3_write_merged_done)) {
            GitHubApi.mergePullRequest(owner, repo, number, method.api, commitMessage?.trim())
        }
    }

    /**
     * Toggle a reaction: read the true state first, then add or delete my own entry.
     * The summary's `total_count` does not say whether I reacted, so list by content and look for me.
     */
    fun toggleReaction(owner: String, repo: String, target: ReactionTarget, kind: ReactionKind) {
        val number = (target as? ReactionTarget.IssueLevel)?.number
            ?: (state.value as? LoadState.Ready)?.value?.issue?.number
            ?: return
        write(owner, repo, number, UiText.res(R.string.m3_reaction_done)) {
            val login = _myLogin.value
            val mine = runCatching {
                when (target) {
                    is ReactionTarget.IssueLevel ->
                        GitHubApi.issueReactions(owner, repo, target.number, kind.api)

                    is ReactionTarget.CommentLevel ->
                        GitHubApi.issueCommentReactions(owner, repo, target.commentId, kind.api)
                }.value.firstOrNull { it.user?.login.equals(login, ignoreCase = true) }
            }.getOrNull()

            if (mine != null) {
                GitHubApi.deleteReaction(mine.id)
            } else {
                when (target) {
                    is ReactionTarget.IssueLevel ->
                        GitHubApi.addIssueReaction(owner, repo, target.number, kind.api)

                    is ReactionTarget.CommentLevel ->
                        GitHubApi.addIssueCommentReaction(owner, repo, target.commentId, kind.api)
                }
            }
            // Record local state only after the call really succeeded.
            val key = reactionKey(target, kind)
            _myReactions.value = if (mine != null) _myReactions.value - key else _myReactions.value + key
        }
    }

    private fun reactionKey(target: ReactionTarget, kind: ReactionKind): String = when (target) {
        is ReactionTarget.IssueLevel -> "issue:${kind.api}"
        is ReactionTarget.CommentLevel -> "comment:${target.commentId}:${kind.api}"
    }

    private fun write(
        owner: String,
        repo: String,
        number: Int,
        success: UiText,
        onSuccess: (() -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { block() } }
            _busy.value = false
            result.onSuccess {
                onSuccess?.invoke()
                _message.value = WriteMessage(success, error = false)
                // Reload so new comments appear and the state pill updates.
                refresh(owner, repo, number)
            }.onFailure {
                _message.value = WriteMessage(writeError(it), error = true)
            }
        }
    }

    private fun writeError(t: Throwable): UiText = when ((t as? ApiException)?.error?.status) {
        405 -> UiText.res(R.string.m3_write_not_mergeable)
        409 -> UiText.res(R.string.m3_write_conflict)
        422 -> UiText.res(R.string.m3_write_rejected)
        else -> t.toUiText()
    }

    private suspend fun fetch(owner: String, repo: String, number: Int): IssueDetailData {
        val issue = GitHubApi.issue(owner, repo, number)
        // Actions (close/edit/manage/merge) are only offered when the API says this account may do
        // them: a read-only visitor on someone else's repository must not see live buttons.
        val permissions = runCatching { GitHubApi.repo(owner, repo).permissions }.getOrNull()
        val comments = runCatching {
            GitHubApi.issueComments(owner, repo, number, 1).value
        }.getOrDefault(emptyList())
        // GitHub's issue endpoint marks PRs; the merge state and diff numbers need the PR object.
        val pr = if (issue.isPullRequest) {
            runCatching { GitHubApi.pullRequest(owner, repo, number) }.getOrNull()
        } else {
            null
        }
        // PR commits: the same model the commit screens use, so tapping one opens the diff.
        val commits = if (pr != null) {
            runCatching { GitHubApi.pullRequestCommits(owner, repo, number, 1).value }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        // Who may edit each comment; fetched once, the account cannot change mid-screen.
        if (_myLogin.value == null) {
            _myLogin.value = runCatching { Session.currentAccount()?.login }.getOrNull()
        }
        return IssueDetailData(issue, pr, comments, commits, permissions)
    }
}
