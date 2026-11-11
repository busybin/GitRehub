package io.github.busybin.gitrehub.compose.discussions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.compose.data.Discussions
import io.github.busybin.gitrehub.core.model.Discussion
import io.github.busybin.gitrehub.core.model.DiscussionAnswerInfo
import io.github.busybin.gitrehub.core.model.DiscussionComment
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One discussion: the opening post plus its (paged) comments.
 *
 * The two are kept in separate states on purpose — a thread with many comments should render its
 * title and body as soon as those arrive, not wait for every page of the discussion.
 */
class DiscussionDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<Discussion>>(LoadState.Loading)
    val state: StateFlow<LoadState<Discussion>> = _state.asStateFlow()

    private val _comments = MutableStateFlow<LoadState<List<DiscussionComment>>>(LoadState.Loading)
    val comments: StateFlow<LoadState<List<DiscussionComment>>> = _comments.asStateFlow()

    private val _commentsLoadingMore = MutableStateFlow(false)
    val commentsLoadingMore: StateFlow<Boolean> = _commentsLoadingMore.asStateFlow()

    private val _commentsHasMore = MutableStateFlow(false)
    val commentsHasMore: StateFlow<Boolean> = _commentsHasMore.asStateFlow()

    private val _answer = MutableStateFlow<DiscussionAnswerInfo?>(null)
    val answer: StateFlow<DiscussionAnswerInfo?> = _answer.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    private val _commentsRefreshing = MutableStateFlow(false)
    val commentsRefreshing: StateFlow<Boolean> = _commentsRefreshing.asStateFlow()

    /** True while a new comment is being sent, so the sheet cannot fire twice. */
    private val _posting = MutableStateFlow(false)
    val posting: StateFlow<Boolean> = _posting.asStateFlow()

    private var key: String = ""
    private var owner: String = ""
    private var repo: String = ""
    private var number: Int = 0

    private val accumulated = mutableListOf<DiscussionComment>()
    private var page = 0
    private var loading = false

    fun load(owner: String, repo: String, number: Int, force: Boolean = false) {
        val newKey = "$owner/$repo#$number"
        if (!force && key == newKey && _state.value is LoadState.Ready) return
        key = newKey
        this.owner = owner
        this.repo = repo
        this.number = number
        loadComments()
        viewModelScope.launch {
            _answer.value = runCatching { GitHubApi.discussionAnswerInfo(owner, repo, number) }.getOrNull()
        }
        viewModelScope.launchLoad({ _state.value = it }) { fetch(owner, repo, number) }
    }

    private suspend fun fetch(owner: String, repo: String, number: Int): Discussion =
        Discussions.detail(owner, repo, number)
            ?: throw IllegalStateException("discussion $number not found")

    fun refreshComments() = loadComments(force = true)

    private fun loadComments(force: Boolean = false) {
        if (loading) return
        if (!force && _comments.value is LoadState.Ready) return
        loading = true
        _commentsRefreshing.value = force
        viewModelScope.launch {
            if (!force) _comments.value = LoadState.Loading
            try {
                val result = withContext(Dispatchers.IO) { Discussions.comments(owner, repo, number, 1) }
                applyComments(result, replace = true)
            } catch (t: Throwable) {
                _comments.value = LoadState.Failure(t.toUiText(), t)
            } finally {
                _commentsRefreshing.value = false
                loading = false
            }
        }
    }

    fun loadMoreComments() {
        if (loading || !_commentsHasMore.value) return
        loading = true
        _commentsLoadingMore.value = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Discussions.comments(owner, repo, number, page + 1)
                }
                applyComments(result, replace = false)
            } catch (_: Throwable) {
                // keep what is already on screen; pulling again retries
            } finally {
                _commentsLoadingMore.value = false
                loading = false
            }
        }
    }

    private fun applyComments(result: PageResult<DiscussionComment>, replace: Boolean) {
        if (replace) {
            accumulated.clear()
            page = 1
        } else {
            page += 1
        }
        accumulated.addAll(result.items)
        _commentsHasMore.value = result.hasMore
        _comments.value = LoadState.Ready(accumulated.toList())
    }

    /**
     * Accept / un-accept [comment]. The REST ids in the list are matched to GraphQL node ids by
     * database id; without a node id the mutation cannot run, so the message explains the failure.
     */
    fun markAnswer(comment: DiscussionComment, accepted: Boolean) {
        val info = _answer.value
        val nodeId = info?.nodeIdOf(comment.id)
        if (info == null || nodeId == null) {
            _message.value = UiText.res(R.string.m3_discussion_answer_failed)
            return
        }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) {
                GitHubApi.setDiscussionAnswer(nodeId, accepted)
            } }
            result
                .onSuccess { _answer.value = info.copy(answerCommentId = if (accepted) comment.id else null) }
                .onFailure { _message.value = it.toUiText() }
        }
    }

    /**
     * Adds a comment through GraphQL (`addDiscussionComment`): discussions have no REST write
     * endpoint, so the node id is resolved first. On success the list is pulled again instead of
     * faking a row — the server also knows who commented and when.
     */
    fun postComment(body: String) {
        val text = body.trim()
        if (text.isEmpty() || _posting.value) return
        _posting.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    GitHubApi.addDiscussionComment(owner, repo, number, text)
                }
            }
            _posting.value = false
            result
                .onSuccess { refreshComments() }
                .onFailure { _message.value = it.toUiText() }
        }
    }

    fun consumeMessage() { _message.value = null }
}
