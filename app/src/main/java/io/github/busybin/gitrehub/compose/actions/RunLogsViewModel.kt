package io.github.busybin.gitrehub.compose.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.toUiText
import io.github.busybin.gitrehub.compose.data.Actions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The run-log zip, decoded into one entry per job/step. */
class RunLogsViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoadState<List<Actions.LogFile>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<Actions.LogFile>>> = _state.asStateFlow()

    private var key = ""

    fun load(owner: String, repo: String, runId: Long, force: Boolean = false) {
        val newKey = "$owner/$repo/$runId"
        if (!force && key == newKey && _state.value is LoadState.Ready) return
        key = newKey
        _state.value = LoadState.Loading
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { Actions.logs(owner, repo, runId) }
            }
            _state.value = result.fold(
                onSuccess = { LoadState.Ready(it) },
                onFailure = { LoadState.Failure(it.toUiText(), it) },
            )
        }
    }
}
