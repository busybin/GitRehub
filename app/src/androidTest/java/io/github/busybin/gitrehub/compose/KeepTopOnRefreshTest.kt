package io.github.busybin.gitrehub.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.compose.common.KeepTopOnRefresh
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opening the feed used to land in the middle of it: the disk snapshot is painted first, then page 1
 * arrives with newer rows prepended, and a keyed LazyColumn keeps the row that was on screen.
 * [KeepTopOnRefresh] snaps back, but only when the list was at the top before the refresh.
 */
@RunWith(AndroidJUnit4::class)
class KeepTopOnRefreshTest {

    @get:Rule
    val compose = createComposeRule()

    private val old = (0 until 40).toList()
    private val newer = (100 until 140).toList()

    private class Screen(val rows: List<Int>, val refreshing: Boolean)

    @Test
    fun prependingRowsLeavesTheListInTheMiddle() {
        var screen by mutableStateOf(Screen(old, false))
        lateinit var listState: LazyListState
        compose.setContent {
            listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(300.dp)) {
                items(screen.rows, key = { it }) { row -> Text("row $row", Modifier.height(48.dp)) }
            }
        }
        compose.waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex)

        screen = Screen(newer + old, false)
        compose.waitForIdle()

        // The row that was on screen is still on screen, 40 rows further down.
        assertTrue(listState.firstVisibleItemIndex > 20)
    }

    @Test
    fun refreshAtTheTopKeepsTheNewestRowsInView() {
        var screen by mutableStateOf(Screen(old, false))
        lateinit var listState: LazyListState
        compose.setContent {
            listState = rememberLazyListState()
            KeepTopOnRefresh(listState, screen.refreshing)
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(300.dp)) {
                items(screen.rows, key = { it }) { row -> Text("row $row", Modifier.height(48.dp)) }
            }
        }
        compose.waitForIdle()

        screen = Screen(old, true)
        compose.waitForIdle()
        screen = Screen(newer + old, false)
        compose.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
    }

    @Test
    fun refreshFurtherDownKeepsTheScrollPosition() {
        var screen by mutableStateOf(Screen(old, false))
        lateinit var listState: LazyListState
        compose.setContent {
            listState = rememberLazyListState()
            KeepTopOnRefresh(listState, screen.refreshing)
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(300.dp)) {
                items(screen.rows, key = { it }) { row -> Text("row $row", Modifier.height(48.dp)) }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { runBlocking { listState.scrollToItem(12) } }
        compose.waitForIdle()

        screen = Screen(old, true)
        compose.waitForIdle()
        screen = Screen(newer + old, false)
        compose.waitForIdle()

        // Still on the row the reader had scrolled to, now shifted by the 40 prepended rows.
        assertEquals(52, listState.firstVisibleItemIndex)
    }
}
