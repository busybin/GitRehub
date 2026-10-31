package io.github.busybin.gitrehub.compose.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.launchLoad
import io.github.busybin.gitrehub.core.model.CompareResult
import io.github.busybin.gitrehub.core.net.GitHubApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Two refs compared by GitHub: what a release bump or a branch sync actually contains. */
class CompareViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<CompareResult>>(LoadState.Loading)
    val state: StateFlow<LoadState<CompareResult>> = _state.asStateFlow()

    private var key = ""

    fun load(owner: String, repo: String, base: String, head: String) {
        val newKey = "$owner/$repo/$base...$head"
        if (newKey == key && _state.value is LoadState.Ready) return
        key = newKey
        viewModelScope.launchLoad({ _state.value = it }) {
            GitHubApi.compare(owner, repo, base, head)
        }
    }
}
