package io.github.busybin.gitrehub.compose.actions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent

/**
 * Full Actions run log, read from the run-log zip the API serves behind a 302.
 *
 * One chip per job/step entry; the body is line-by-line so a log with tens of thousands of lines
 * still scrolls smoothly.
 */
@Composable
fun RunLogsScreen(owner: String, repo: String, runId: Long) {
    val vm: RunLogsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(owner, repo, runId) { vm.load(owner, repo, runId) }

    AsyncContent(state = state, onRetry = { vm.load(owner, repo, runId, force = true) }) { files ->
        if (files.isEmpty()) {
            Text(
                text = stringResource(R.string.m3_actions_no_logs),
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AsyncContent
        }
        val index = selected.coerceIn(0, files.size - 1)
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files.size) { i ->
                    FilterChip(
                        selected = i == index,
                        onClick = { selected = i },
                        label = { Text(files[i].name) },
                    )
                }
            }
            val lines = remember(files, index) { files[index].text.lines() }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                items(lines.size) { line ->
                    Text(
                        text = lines[line],
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}

