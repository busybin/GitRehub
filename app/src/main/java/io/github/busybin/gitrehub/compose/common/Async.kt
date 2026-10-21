package io.github.busybin.gitrehub.compose.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs [block] and funnels the outcome into a [LoadState] setter.
 * All network/DB work happens on IO, the state is published back on the caller's dispatcher.
 */
fun <T> CoroutineScope.launchLoad(
    setState: (LoadState<T>) -> Unit,
    block: suspend () -> T,
) {
    launch {
        setState(LoadState.Loading)
        try {
            val value = withContext(Dispatchers.IO) { block() }
            setState(LoadState.Ready(value))
        } catch (t: Throwable) {
            setState(LoadState.Failure(t.toUiText(), t))
        }
    }
}
