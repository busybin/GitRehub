package io.github.busybin.gitrehub.compose.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/*
 * A keyed LazyColumn keeps the row that was on screen visible when rows are inserted above it. That
 * is what you want while scrolling, but not on a refresh: page 1 comes back with newer rows
 * prepended, so a list opened at the top ends up scrolled into the middle of itself.
 *
 * Both helpers work on row indices, so they are density independent.
 */

/** Rows below this index still count as "the list is at the top". */
private const val TOP_ROWS = 2

/**
 * Jumps back to the top when a refresh finishes, but only if the list was at the top when the
 * refresh started. A refresh from further down keeps its place: the user scrolled there on purpose.
 */
@Composable
fun KeepTopOnRefresh(listState: LazyListState, refreshing: Boolean) {
    var wasAtTop by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            wasAtTop = listState.firstVisibleItemIndex <= TOP_ROWS
        } else if (wasAtTop) {
            wasAtTop = false
            listState.scrollToItem(0)
        }
    }
}

/** Whether the list has been scrolled far enough for a "back to top" button to be worth showing. */
@Composable
fun rememberShowBackToTop(listState: LazyListState, rows: Int = 6): Boolean {
    val show by remember(listState, rows) {
        derivedStateOf { listState.firstVisibleItemIndex > rows }
    }
    return show
}
