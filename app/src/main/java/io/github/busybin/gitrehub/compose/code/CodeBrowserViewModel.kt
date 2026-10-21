package io.github.busybin.gitrehub.compose.code

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.core.model.CodeEntry
import io.github.busybin.gitrehub.compose.data.RepoContent
import io.github.busybin.gitrehub.compose.data.RepoStats
import io.github.busybin.gitrehub.core.model.Commit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * recursive tree download).
 */
class CodeBrowserViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<CodeListing>>(LoadState.Loading)
    val state: StateFlow<LoadState<CodeListing>> = _state.asStateFlow()

    private var owner: String = ""
    private var repo: String = ""
    private var ref: String? = null
    private var path: String = ""

    fun setup(owner: String, repo: String, ref: String?) {
        val same = this.owner == owner && this.repo == repo && this.ref == ref
        this.owner = owner
        this.repo = repo
        this.ref = ref
        if (same && _state.value is LoadState.Ready) return
        load("")
    }

    fun open(path: String) = load(path)

    fun refresh() = load(path)

    private fun load(newPath: String) {
        path = newPath
        val owner = owner
        val repo = repo
        val ref = ref
        val path = path
        viewModelScope.launchLoad({ _state.value = it }) {
            coroutineScope {
                val entries = async { RepoContent.list(owner, repo, path, ref) }
                val lastCommit = async { RepoStats.lastCommit(owner, repo, path, ref) }
                CodeListing(ref, path, entries.await(), lastCommit.await())
            }
        }
    }
}
