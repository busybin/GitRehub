package io.github.busybin.gitrehub.compose.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One page of results from GitHub's page/per_page pagination. */
data class PageResult<T>(val items: List<T>, val hasMore: Boolean)

/**
 * Minimal paging ViewModel: [refresh] reloads from page 1, [loadMore] appends the next page.
 *
 * A refresh keeps the previously loaded rows on screen (the pull-to-refresh indicator reports the
 * progress) so scrolling does not jump back to a full-screen spinner.
 */
abstract class PagedViewModel<T> : ViewModel() {

    private val _state = MutableStateFlow<LoadState<List<T>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<T>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val accumulated = mutableListOf<T>()
    private var page = 0
    private var inFlight = false

    protected abstract suspend fun fetch(page: Int): PageResult<T>

    /**
     * Optional: the same page, served **only** from the HTTP disk cache (no network).
     *
     * When a screen implements this, [refresh] paints it first, so opening the app shows the last
     * visit immediately (with the refresh indicator running) instead of an empty skeleton while the
     * request goes out. Returning null/empty means "nothing on disk" and changes nothing.
     * See `RestProvider.get*ServiceCached` for the usual implementation.
     */
    protected open suspend fun fetchCached(page: Int): PageResult<T>? = null

    /** Hook for subclasses that need to prepare (e.g. resolve the logged-in login) before fetching. */
    protected open suspend fun prepare() {}

    /** Hook for subclasses that can explain a specific failure (e.g. a 404 that means "not allowed"). */
    protected open fun errorText(t: Throwable): UiText = t.toUiText()

    fun refresh() {
        if (inFlight) return
        inFlight = true
        viewModelScope.launch {
            val hadContent = _state.value is LoadState.Ready
            if (hadContent) _refreshing.value = true else _state.value = LoadState.Loading
            try {
                // prepare() may fetch (e.g. the default branch), so it cannot run on main.
                withContext(Dispatchers.IO) { prepare() }
                // Cold start: show what the disk already has, then revalidate.
                if (!hadContent) paintCached()
                val result = withContext(Dispatchers.IO) { fetch(1) }
                accumulated.clear()
                accumulated.addAll(result.items)
                page = 1
                _hasMore.value = result.hasMore
                _state.value = LoadState.Ready(accumulated.toList())
            } catch (t: Throwable) {
                // Anything already on screen (the disk snapshot included) stays: a failed refresh
                // should not throw away readable content.
                if (_state.value !is LoadState.Ready) {
                    _state.value = LoadState.Failure(errorText(t), t)
                }
            } finally {
                _refreshing.value = false
                inFlight = false
            }
        }
    }

    /** Paints [fetchCached] when it has something; the caller keeps the network pass going. */
    private suspend fun paintCached() {
        val cached = runCatching { withContext(Dispatchers.IO) { fetchCached(1) } }.getOrNull() ?: return
        if (cached.items.isEmpty()) return
        accumulated.clear()
        accumulated.addAll(cached.items)
        page = 1
        _hasMore.value = cached.hasMore
        _state.value = LoadState.Ready(accumulated.toList())
        // The network pass is still coming, so keep a spinner on screen.
        _refreshing.value = true
    }

    fun loadMore() {
        if (inFlight || !_hasMore.value || _loadingMore.value) return
        if (_state.value !is LoadState.Ready) return
        inFlight = true
        _loadingMore.value = true
        viewModelScope.launch {
            try {
                val next = page + 1
                val result = withContext(Dispatchers.IO) { fetch(next) }
                if (result.items.isEmpty()) {
                    _hasMore.value = false
                } else {
                    accumulated.addAll(result.items)
                    page = next
                    _hasMore.value = result.hasMore
                    _state.value = LoadState.Ready(accumulated.toList())
                }
            } catch (_: Throwable) {
                // keep what we already have; the user can pull to refresh
            } finally {
                _loadingMore.value = false
                inFlight = false
            }
        }
    }

    /** Replaces a single item in place (e.g. after toggling state). */
    protected fun replaceItem(predicate: (T) -> Boolean, transform: (T) -> T) {
        val index = accumulated.indexOfFirst(predicate)
        if (index >= 0) {
            accumulated[index] = transform(accumulated[index])
            _state.value = LoadState.Ready(accumulated.toList())
        }
    }

    /** Replaces a single item in place by index (used by swipe actions). */
    protected fun replaceAt(index: Int, transform: (T) -> T) {
        if (index in accumulated.indices) {
            accumulated[index] = transform(accumulated[index])
            _state.value = LoadState.Ready(accumulated.toList())
        }
    }

    /** Replaces the whole list without touching the network (used after bulk actions). */
    protected fun publish(items: List<T>) {
        accumulated.clear()
        accumulated.addAll(items)
        _state.value = LoadState.Ready(accumulated.toList())
    }

    /** Snapshot of everything currently loaded (used by bulk actions such as mark-all-read). */
    protected fun currentItems(): List<T> = accumulated.toList()
}
