package io.github.busybin.gitrehub.compose.discussions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.PageResult
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.model.Discussion
import io.github.busybin.gitrehub.core.model.DiscussionComment
import io.github.busybin.gitrehub.compose.data.Discussions
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

    private val _commentsRefreshing = MutableStateFlow(false)
    val commentsRefreshing: StateFlow<Boolean> = _commentsRefreshing.asStateFlow()

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
}
