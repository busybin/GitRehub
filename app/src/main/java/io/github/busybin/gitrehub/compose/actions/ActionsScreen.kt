package io.github.busybin.gitrehub.compose.actions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.FailureView
import io.github.busybin.gitrehub.compose.common.LinkCopyDialog
import io.github.busybin.gitrehub.compose.common.KeepTopOnRefresh
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.SkeletonList
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.model.WorkflowJob
import io.github.busybin.gitrehub.core.model.WorkflowRun
import io.github.busybin.gitrehub.helper.BrowserLauncher

/**
 * Actions: the workflow runs of a repository, with the jobs/steps of a run one tap away.
 *
 * The run **log** itself has no JSON endpoint (the API hands out a zip behind a 302), so the native
 * view stops at per-step status; the full log stays one tap away on github.com.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(owner: String, repo: String) {
    val vm: ActionsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val workflows by vm.workflows.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    KeepTopOnRefresh(listState, refreshing)
    val tokens = LocalTokens.current
    val selectedWorkflow = vm.currentWorkflowId()

    LaunchedEffect(owner, repo) { vm.setup(owner, repo) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore) {
        if (shouldLoadMore && hasMore) vm.loadMore()
    }

    Column(Modifier.fillMaxWidth()) {
        val workflowList = (workflows as? LoadState.Ready)?.value.orEmpty()
        if (workflowList.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = selectedWorkflow == null,
                        onClick = { vm.selectWorkflow(null) },
                        label = { Text(stringResource(R.string.m3_actions_all)) },
                    )
                }
                items(workflowList, key = { it.id }) { workflow ->
                    FilterChip(
                        selected = selectedWorkflow == workflow.id,
                        onClick = { vm.selectWorkflow(workflow.id) },
                        label = {
                            Text(
                                text = workflow.name ?: workflow.path.orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            when (val current = state) {
                is LoadState.Loading -> SkeletonList()

                is LoadState.Failure -> FailureView(current.message, onRetry = { vm.refresh() })

                is LoadState.Ready -> {
                    val runs = current.value
                    if (runs.isEmpty()) {
                        EmptyView(stringResource(R.string.m3_actions_no_runs))
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(tokens.listPadding),
                            verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                        ) {
                            items(runs, key = { it.id }) { run ->
                                RunCard(
                                    run = run,
                                    jobsState = jobs[run.id],
                                    onExpand = { vm.loadJobs(run.id) },
                                )
                            }
                            if (loadingMore) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunCard(
    run: WorkflowRun,
    jobsState: LoadState<List<WorkflowJob>>?,
    onExpand: () -> Unit,
) {
    val tokens = LocalTokens.current
    val context = LocalContext.current
    var expanded by rememberSaveable(run.id) { mutableStateOf(false) }
    var pendingLink by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(expanded) { if (expanded) onExpand() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = tokens.cardShape,
    ) {
        Column(Modifier.padding(tokens.cardPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusPill(run.statusWord)
                        Text(
                            text = run.name ?: run.path.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!run.displayTitle.isNullOrBlank() && run.displayTitle != run.name) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = run.displayTitle.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val meta = buildString {
                        append("#${run.runNumber}")
                        run.headBranch?.let { append(" · $it") }
                        run.event?.let { append(" · $it") }
                        append(" · ${relativeTime(run.createdAt)}")
                    }
                    MetaText(meta)
                }
                if (run.actor?.login != null) {
                    Avatar(run.actor.avatarUrl, size = if (tokens.compact) 22.dp else 26.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.m3_collapse else R.string.m3_expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    JobsSection(jobsState)
                    run.htmlUrl?.let { url ->
                        TextButton(onClick = { if (!BrowserLauncher.open(context, url)) pendingLink = url }) {
                            Icon(
                                Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.m3_open_on_github))
                        }
                    }
                }
            }
        }
    }

    pendingLink?.let { LinkCopyDialog(url = it, onDismiss = { pendingLink = null }) }
}

@Composable
private fun JobsSection(state: LoadState<List<WorkflowJob>>?) {
    when (state) {
        null, is LoadState.Loading -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }

        is LoadState.Failure -> Text(
            text = stringResource(R.string.m3_actions_jobs_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is LoadState.Ready -> {
            val jobs = state.value
            if (jobs.isEmpty()) {
                Text(
                    text = stringResource(R.string.m3_actions_no_jobs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                jobs.forEach { job -> JobRow(job) }
            }
        }
    }
}

@Composable
private fun JobRow(job: WorkflowJob) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = job.name.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusPill(job.statusWord)
        }
        val steps = job.steps.orEmpty()
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            steps.forEach { step ->
                Row(
                    modifier = Modifier.padding(start = 10.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MetaText("·")
                    Text(
                        text = step.name.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    step.statusWord?.takeIf { RunStatus.of(it) != RunStatus.SUCCESS }?.let {
                        Pill(it.replace('_', ' '))
                    }
                }
            }
            if (job.completedAt != null) {
                Spacer(Modifier.height(4.dp))
                MetaText(stringResource(R.string.m3_actions_finished, relativeTime(job.completedAt)))
            }
        }
    }
}
