package io.github.busybin.gitrehub.compose.code

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.core.model.CodeEntry
import io.github.busybin.gitrehub.core.model.GitTree
import io.github.busybin.gitrehub.compose.data.RepoContent
import io.github.busybin.gitrehub.compose.data.RepoStats
import io.github.busybin.gitrehub.core.model.Commit
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CodeListing(
    /** null = the repository's default branch. */
    val ref: String?,
    val path: String,
    val entries: List<CodeEntry>,
    /** Latest commit in this directory (the web UI's "last modified" line). */
    val lastCommit: Commit? = null,
)

/**
 * Navigation state of the code browser: which directory of which ref is on screen.
 *
 * Only one directory is requested at a time, so browsing a huge repository stays cheap (no full
 * recursive tree download). Listings are kept in a small LRU, so walking back up a tree (or
 * re-entering a directory you just left) does not hit the network again; [refresh] always does.
 */
class CodeBrowserViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<CodeListing>>(LoadState.Loading)
    val state: StateFlow<LoadState<CodeListing>> = _state.asStateFlow()

    /** The whole file list of the current ref, loaded on demand by the file finder. */
    private val _fileIndex = MutableStateFlow<LoadState<GitTree>?>(null)
    val fileIndex: StateFlow<LoadState<GitTree>?> = _fileIndex.asStateFlow()

    private var owner: String = ""
    private var repo: String = ""
    private var ref: String? = null
    private var path: String = ""

    /** Load generation; an answer for a directory the user already left is dropped. */
    private var generation = 0

    /** `ref|path` → listing; browsing rarely needs more than the current branch. */
    private val listings = object : LinkedHashMap<String, CodeListing>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CodeListing>) =
            size > MAX_CACHED_LISTINGS
    }

    fun setup(owner: String, repo: String, ref: String?) {
        val same = this.owner == owner && this.repo == repo && this.ref == ref
        this.owner = owner
        this.repo = repo
        this.ref = ref
        if (!same) {
            listings.clear()
            _fileIndex.value = null
        }
        if (same && _state.value is LoadState.Ready) return
        load("", force = false)
    }

    /** Fetch the recursive tree once; the finder filters it locally afterwards. */
    fun loadFileIndex(force: Boolean = false) {
        if (!force && (_fileIndex.value is LoadState.Ready || _fileIndex.value is LoadState.Loading)) {
            return
        }
        val owner = owner
        val repo = repo
        val ref = ref
        viewModelScope.launchLoad({ _fileIndex.value = it }) {
            GitHubApi.gitTree(owner, repo, ref)
        }
    }

    fun open(path: String) = load(path, force = false)

    fun refresh() = load(path, force = true)

    private fun load(newPath: String, force: Boolean) {
        path = newPath
        val generation = ++generation
        val key = cacheKey(newPath)
        if (!force) {
            listings[key]?.let {
                _state.value = LoadState.Ready(it)
                return
            }
        }
        val owner = owner
        val repo = repo
        val ref = ref
        viewModelScope.launch {
            _state.value = LoadState.Loading
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        val entries = async { RepoContent.list(owner, repo, newPath, ref) }
                        val lastCommit = async { RepoStats.lastCommit(owner, repo, newPath, ref) }
                        CodeListing(ref, newPath, entries.await(), lastCommit.await())
                    }
                }
            }
            if (generation != this@CodeBrowserViewModel.generation) return@launch
            result.onSuccess { listing ->
                listings[key] = listing
                _state.value = LoadState.Ready(listing)
            }.onFailure { error ->
                _state.value = LoadState.Failure(error.toUiText(), error)
            }
        }
    }

    private fun cacheKey(p: String) = "${ref.orEmpty()}|$p"

    private companion object {
        const val MAX_CACHED_LISTINGS = 24
    }
}
