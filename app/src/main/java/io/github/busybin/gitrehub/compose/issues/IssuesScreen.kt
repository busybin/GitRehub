package io.github.busybin.gitrehub.compose.issues

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.isWideLayout
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.model.Issue

/**
 * Issue / PR list. On wide screens the list and detail sit side by side, so picking a row only
 * swaps the right column.
 */
@Composable
fun IssuesScreen(
    owner: String,
    repo: String,
    kind: IssueKind = IssueKind.ISSUE,
    onOpenIssue: (Int) -> Unit,
    onNewIssue: (() -> Unit)? = null,
    refreshSignal: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
) {
    val wide = isWideLayout()
    var selected by remember { mutableStateOf<Int?>(null) }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.42f)) {
                IssuesList(
                    owner = owner,
                    repo = repo,
                    kind = kind,
                    selectedNumber = selected,
                    modifier = Modifier.fillMaxSize(),
                    onOpenIssue = { selected = it },
                    onNewIssue = onNewIssue,
                    refreshSignal = refreshSignal,
                    onRefreshConsumed = onRefreshConsumed,
                )
            }
            VerticalDivider()
            Box(Modifier.weight(0.58f)) {
                val number = selected
                if (number == null) {
                    EmptyView(text = stringResource(R.string.m3_issue_pick_one))
                } else {
                    // Key on the number so the detail ViewModel is recreated when the issue changes.
                    key(number) {
                        IssueDetailScreen(owner = owner, repo = repo, number = number)
                    }
                }
            }
        }
    } else {
        IssuesList(
            owner = owner,
            repo = repo,
            kind = kind,
            selectedNumber = null,
            modifier = Modifier.fillMaxSize(),
            onOpenIssue = onOpenIssue,
            onNewIssue = onNewIssue,
            refreshSignal = refreshSignal,
            onRefreshConsumed = onRefreshConsumed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssuesList(
    owner: String,
    repo: String,
    kind: IssueKind,
    selectedNumber: Int?,
    modifier: Modifier = Modifier,
    onOpenIssue: (Int) -> Unit,
    onNewIssue: (() -> Unit)? = null,
    refreshSignal: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
) {
    val vm: IssuesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val query by vm.query.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val bulkBusy by vm.bulkBusy.collectAsStateWithLifecycle()
    val bulkMessage by vm.message.collectAsStateWithLifecycle()
    var checked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showLabelPicker by remember { mutableStateOf(false) }
    var showAssigneePicker by remember { mutableStateOf(false) }
    val tokens = LocalTokens.current

    LaunchedEffect(owner, repo, kind) { vm.setup(owner, repo, kind) }
    LaunchedEffect(bulkMessage) {
        if (bulkMessage != null) {
            kotlinx.coroutines.delay(4000)
            vm.consumeMessage()
        }
    }
    // The selection starts empty again when the list is replaced (filter change, repo change).
    LaunchedEffect(owner, repo, kind, query) { checked = emptySet() }

    // Coming back after creating an issue elsewhere: the list is stale, so refresh it.
    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            vm.refresh()
            onRefreshConsumed()
        }
    }

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

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IssueFilter.entries.forEach { entry ->
                    FilterChip(
                        selected = query.state == entry,
                        onClick = { vm.setFilter(entry) },
                        label = { Text(stringResource(entry.labelRes)) },
                    )
                }
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = query.activeCount > 0,
                    onClick = {
                        vm.loadOptions()
                        showFilters = true
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = {
                        Text(
                            if (query.activeCount > 0) {
                                stringResource(R.string.m3_issue_filters_count, query.activeCount)
                            } else {
                                stringResource(R.string.m3_issue_filters)
                            }
                        )
                    },
                )
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                AsyncContent(state = state, onRetry = { vm.refresh() }) { issues ->
                    if (issues.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.m3_no_content), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        return@AsyncContent
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(tokens.listPadding),
                        verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                    ) {
                        items(issues, key = { it.id }) { issue ->
                            IssueRow(
                                issue = issue,
                                selected = issue.number == selectedNumber,
                                checked = issue.number in checked,
                                selectionMode = checked.isNotEmpty(),
                                onLongPress = {
                                    checked = if (issue.number in checked) {
                                        checked - issue.number
                                    } else {
                                        checked + issue.number
                                    }
                                },
                                onOpenIssue = { number ->
                                    if (checked.isEmpty()) {
                                        onOpenIssue(number)
                                    } else {
                                        checked = if (number in checked) checked - number else checked + number
                                    }
                                },
                            )
                        }
                        if (loadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                            }
                        }
                    }
                }
            }
        }
        if (checked.isNotEmpty()) {
            BulkBar(
                count = checked.size,
                busy = bulkBusy,
                message = bulkMessage?.render(),
                onClose = { vm.bulkClose(checked, closed = true) },
                onReopen = { vm.bulkClose(checked, closed = false) },
                onLabel = {
                    vm.loadOptions()
                    showLabelPicker = true
                },
                onAssign = {
                    vm.loadOptions()
                    showAssigneePicker = true
                },
                onClear = { checked = emptySet() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
        }
        // New-issue FAB only makes sense on the issues tab; a PR cannot be created from here.
        if (onNewIssue != null && kind == IssueKind.ISSUE && checked.isEmpty()) {
            FloatingActionButton(
                onClick = onNewIssue,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.m3_new_issue))
            }
        }
    }
    if (showLabelPicker) {
        BulkPickerDialog(
            title = stringResource(R.string.m3_bulk_label),
            options = options.labels.map { it.name.orEmpty() }.filter { it.isNotBlank() },
            onDismiss = { showLabelPicker = false },
            onPick = { label ->
                showLabelPicker = false
                val existing = (state as? LoadState.Ready)?.value.orEmpty()
                    .filter { it.number in checked }
                    .associate { it.number to it.labels.mapNotNull { labelItem -> labelItem.name } }
                vm.bulkAddLabel(checked, label, existing)
                checked = emptySet()
            },
        )
    }
    if (showAssigneePicker) {
        BulkPickerDialog(
            title = stringResource(R.string.m3_bulk_assign),
            options = options.assignees.mapNotNull { it.login },
            onDismiss = { showAssigneePicker = false },
            onPick = { login ->
                showAssigneePicker = false
                vm.bulkAssign(checked, listOf(login))
                checked = emptySet()
            },
        )
    }
    if (showFilters) {
        IssueFilterSheet(
            query = query,
            options = options,
            onChange = { vm.setQuery(it) },
            onDismiss = { showFilters = false },
        )
    }
}

/**
 * Server-side issue filters: state, sort, labels, assignee and milestone. Every change applies at
 * once (the list refreshes behind the sheet), so there is no "apply" button to forget.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun IssueFilterSheet(
    query: IssueQuery,
    options: IssueFilterOptions,
    onChange: (IssueQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.m3_issue_filters), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))

            FilterGroupTitle(stringResource(R.string.m3_filter_state))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IssueFilter.entries.forEach { entry ->
                    FilterChip(
                        selected = query.state == entry,
                        onClick = { onChange(query.copy(state = entry)) },
                        label = { Text(stringResource(entry.labelRes)) },
                    )
                }
            }

            FilterGroupTitle(stringResource(R.string.m3_filter_sort))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IssueSort.entries.forEach { entry ->
                    FilterChip(
                        selected = query.sort == entry,
                        onClick = { onChange(query.copy(sort = entry)) },
                        label = { Text(stringResource(entry.labelRes)) },
                    )
                }
                FilterChip(
                    selected = query.descending,
                    onClick = { onChange(query.copy(descending = true)) },
                    label = { Text(stringResource(R.string.m3_issue_sort_desc)) },
                )
                FilterChip(
                    selected = !query.descending,
                    onClick = { onChange(query.copy(descending = false)) },
                    label = { Text(stringResource(R.string.m3_issue_sort_asc)) },
                )
            }

            if (options.labels.isNotEmpty()) {
                FilterGroupTitle(stringResource(R.string.m3_filter_labels))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.labels.forEach { label ->
                        val name = label.name.orEmpty()
                        FilterChip(
                            selected = name in query.labels,
                            onClick = {
                                val next = if (name in query.labels) {
                                    query.labels - name
                                } else {
                                    query.labels + name
                                }
                                onChange(query.copy(labels = next))
                            },
                            label = { Text(name) },
                        )
                    }
                }
            }

            if (options.assignees.isNotEmpty()) {
                FilterGroupTitle(stringResource(R.string.m3_filter_assignee))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = query.assignee == null,
                        onClick = { onChange(query.copy(assignee = null)) },
                        label = { Text(stringResource(R.string.m3_filter_any)) },
                    )
                    FilterChip(
                        selected = query.assignee == IssueQuery.NOBODY,
                        onClick = { onChange(query.copy(assignee = IssueQuery.NOBODY)) },
                        label = { Text(stringResource(R.string.m3_filter_nobody)) },
                    )
                    options.assignees.forEach { user ->
                        val login = user.login.orEmpty()
                        FilterChip(
                            selected = query.assignee == login,
                            onClick = {
                                onChange(
                                    query.copy(
                                        assignee = if (query.assignee == login) null else login
                                    )
                                )
                            },
                            label = { Text(login) },
                        )
                    }
                }
            }

            if (options.milestones.isNotEmpty()) {
                FilterGroupTitle(stringResource(R.string.m3_filter_milestone))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = query.milestone == null,
                        onClick = { onChange(query.copy(milestone = null)) },
                        label = { Text(stringResource(R.string.m3_filter_any)) },
                    )
                    FilterChip(
                        selected = query.milestone == IssueQuery.NO_MILESTONE,
                        onClick = { onChange(query.copy(milestone = IssueQuery.NO_MILESTONE)) },
                        label = { Text(stringResource(R.string.m3_filter_none)) },
                    )
                    options.milestones.forEach { milestone ->
                        FilterChip(
                            selected = query.milestone == milestone.number,
                            onClick = {
                                onChange(
                                    query.copy(
                                        milestone = if (query.milestone == milestone.number) {
                                            null
                                        } else {
                                            milestone.number
                                        }
                                    )
                                )
                            },
                            label = { Text(milestone.title.orEmpty()) },
                        )
                    }
                }
            }

            if (options.failed) {
                Spacer(Modifier.height(10.dp))
                MetaText(stringResource(R.string.m3_new_issue_options_failed))
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChange(IssueQuery()) }) {
                    Text(stringResource(R.string.m3_filter_clear))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.m3_close))
                }
            }
        }
    }
}

@Composable
private fun FilterGroupTitle(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun IssueRow(
    issue: Issue,
    onOpenIssue: (Int) -> Unit,
    selected: Boolean = false,
    checked: Boolean = false,
    selectionMode: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val tokens = LocalTokens.current
    val isPullRequest = issue.isPullRequest
    val closed = issue.isClosed
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onOpenIssue(issue.number) },
                onLongClick = onLongPress,
            ),
        colors = CardDefaults.cardColors(
            // Highlight the row shown in the right column on wide screens.
            containerColor = when {
                checked -> MaterialTheme.colorScheme.primaryContainer
                selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        shape = tokens.cardShape,
    ) {
        Row(
            modifier = Modifier.padding(tokens.cardPadding),
            verticalAlignment = Alignment.Top,
        ) {
            if (selectionMode) {
                Checkbox(checked = checked, onCheckedChange = { onLongPress?.invoke() })
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                imageVector = if (closed) {
                    if (isPullRequest) Icons.Outlined.CallMerge else Icons.Outlined.TaskAlt
                } else {
                    Icons.Outlined.ErrorOutline
                },
                contentDescription = null,
                tint = if (closed) MaterialTheme.colorScheme.outline
                else Color(0xFF1A7F37),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = issue.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetaText("#${issue.number} · ${relativeTime(issue.createdAt)}")
                    if (isPullRequest) Pill(stringResource(R.string.m3_notif_pr))
                    if (issue.comments > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(3.dp))
                            MetaText("${issue.comments}")
                        }
                    }
                }
                val labels = issue.labels
                if (labels.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        labels.take(3).forEach { label ->
                            Pill(label.name.orEmpty())
                        }
                    }
                }
            }
            Avatar(issue.user?.avatarUrl, size = if (tokens.compact) 22.dp else 28.dp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Bottom bar of the multi-select mode: every action applies to the whole selection. */
@Composable
private fun BulkBar(
    count: Int,
    busy: Boolean,
    message: String?,
    onClose: () -> Unit,
    onReopen: () -> Unit,
    onLabel: () -> Unit,
    onAssign: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.m3_bulk_selected, count),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.m3_bulk_clear)) }
            }
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                TextButton(onClick = onClose, enabled = !busy) {
                    Text(stringResource(R.string.m3_bulk_close))
                }
                TextButton(onClick = onReopen, enabled = !busy) {
                    Text(stringResource(R.string.m3_bulk_reopen))
                }
                TextButton(onClick = onLabel, enabled = !busy) {
                    Text(stringResource(R.string.m3_bulk_label))
                }
                TextButton(onClick = onAssign, enabled = !busy) {
                    Text(stringResource(R.string.m3_bulk_assign))
                }
            }
        }
    }
}

/** Single-choice picker for the bulk label / assignee actions. */
@Composable
private fun BulkPickerDialog(
    title: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (options.isEmpty()) {
                Text(stringResource(R.string.m3_bulk_no_options))
            } else {
                Column(
                    Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { option ->
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(option) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_cancel)) }
        },
    )
}
