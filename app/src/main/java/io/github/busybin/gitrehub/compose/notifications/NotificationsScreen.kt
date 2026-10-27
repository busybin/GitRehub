package io.github.busybin.gitrehub.compose.notifications

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.KeepTopOnRefresh
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.ReadState
import io.github.busybin.gitrehub.compose.common.SwipeAction
import io.github.busybin.gitrehub.compose.common.SwipePrefs
import io.github.busybin.gitrehub.compose.common.ThreadKey
import io.github.busybin.gitrehub.compose.common.isWideLayout
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.compose.common.rememberShowBackToTop
import io.github.busybin.gitrehub.compose.code.CommitDetailScreen
import io.github.busybin.gitrehub.compose.discussions.DiscussionDetailScreen
import io.github.busybin.gitrehub.compose.issues.IssueDetailScreen
import io.github.busybin.gitrehub.core.model.Notification
import io.github.busybin.gitrehub.provider.scheme.SchemeParser
import kotlinx.coroutines.launch

/**
 * Notifications screen: narrow down what concerns me without leaving the screen (type chips, repo
 * dropdown, reason filter in the sheet); grouping (repo / time) is display only. Filter state
 * lives in MMKV ([NotificationFilterPrefs]).
 *
 * On wide screens the list and thread detail sit side by side; picking a row only swaps the right column.
 */
@Composable
fun NotificationsScreen(
    onOpenIssue: (owner: String, repo: String, number: Int) -> Unit = { _, _, _ -> },
    onOpenDiscussion: (owner: String, repo: String, number: Int) -> Unit = { _, _, _ -> },
    onOpenCommit: (owner: String, repo: String, sha: String) -> Unit = { _, _, _ -> },
    onOpenReleases: (owner: String, repo: String) -> Unit = { _, _ -> },
) {
    val wide = isWideLayout()
    // Thread shown in the right column on wide screens; always null on small screens.
    var selected by remember { mutableStateOf<Selection?>(null) }

    val openThread: (Notification, SubjectTarget) -> Unit = { item, target ->
        when {
            // Thread kinds with no native screen (e.g. security alert); the row offers "open in browser".
            target.kind !in SUBJECT_KINDS -> Unit
            // Commit notifications open the commit detail screen.
            target.kind == "commits" && target.ref != null ->
                if (wide) selected = Selection(item.id, target)
                else onOpenCommit(target.owner, target.repo, target.ref)
            // Release notifications land on the releases list, one step closer than the browser.
            target.kind == "releases" -> onOpenReleases(target.owner, target.repo)
            wide && target.number != null -> selected = Selection(item.id, target)
            target.kind == "discussions" && target.number != null ->
                onOpenDiscussion(target.owner, target.repo, target.number)
            (target.kind == "issues" || target.kind == "pulls") && target.number != null ->
                onOpenIssue(target.owner, target.repo, target.number)
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.42f)) {
                NotificationsList(
                    selectedId = selected?.notificationId,
                    modifier = Modifier.fillMaxSize(),
                    onOpen = openThread,
                )
            }
            VerticalDivider()
            Box(Modifier.weight(0.58f)) {
                val thread = selected?.target
                val number = thread?.number
                val ref = thread?.ref
                when {
                    thread == null || (number == null && ref == null) ->
                        EmptyView(text = stringResource(R.string.m3_notif_pick_one))

                    thread.kind == "commits" && ref != null ->
                        // Key on the thread so the detail ViewModel is recreated when it changes.
                        key(thread.key()) {
                            CommitDetailScreen(owner = thread.owner, repo = thread.repo, sha = ref)
                        }

                    number != null -> key(thread.key()) {
                        if (thread.kind == "discussions") {
                            DiscussionDetailScreen(owner = thread.owner, repo = thread.repo, number = number)
                        } else {
                            IssueDetailScreen(owner = thread.owner, repo = thread.repo, number = number)
                        }
                    }

                    else -> EmptyView(text = stringResource(R.string.m3_notif_pick_one))
                }
            }
        }
    } else {
        NotificationsList(
            selectedId = null,
            modifier = Modifier.fillMaxSize(),
            onOpen = openThread,
        )
    }
}

/** Which thread the right column shows; the notification id highlights it in the list. */
private data class Selection(val notificationId: String, val target: SubjectTarget)

/** The list itself (filter bar + list + mark-all-read FAB); takes the left half on wide screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsList(
    selectedId: String?,
    modifier: Modifier = Modifier,
    onOpen: (Notification, SubjectTarget) -> Unit,
) {
    val vm: NotificationsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val filter by NotificationFilterPrefs.filter.collectAsStateWithLifecycle()
    val swipeStart by SwipePrefs.start.collectAsStateWithLifecycle()
    val ignoredMap by vm.ignored.collectAsStateWithLifecycle()
    val swipeEnd by SwipePrefs.end.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    KeepTopOnRefresh(listState, refreshing)
    val showBackToTop = rememberShowBackToTop(listState)
    val context = LocalContext.current
    val tokens = LocalTokens.current
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    // Collapsed sections, only used when grouping; not persisted so they start expanded.
    var collapsed by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(Unit) { vm.refresh() }

    val readKeys by ReadState.readKeys.collectAsStateWithLifecycle()
    val readKeyTimes by ReadState.readKeyTimes.collectAsStateWithLifecycle()
    // Server unread is not the whole truth: a thread read from the activity feed (or read earlier
    // and only re-fetched from the stale cache) is already recorded locally. Applying it here keeps
    // "read" stable across restarts, and the timestamp lets genuinely new activity come back unread.
    val all = remember(state, readKeys, readKeyTimes) {
        (state as? LoadState.Ready)?.value.orEmpty().map { item ->
            val locallyRead = item.unread && ThreadKey.isReadLocally(
                apiUrl = item.subject?.url,
                updatedAt = item.updatedAt?.toEpochMilli(),
                keys = readKeys,
                readAt = readKeyTimes,
            )
            if (locallyRead) item.copy(unread = false) else item
        }
    }
    // Repo candidates with their totals, computed from all notifications, ignoring the current filter.
    val repoCounts: Map<String, Int> = all
        .mapNotNull { it.repoFullName }
        .groupingBy { it }
        .eachCount()
        .toSortedMap()
    // Repo + reason matches: chip counts are based on this, otherwise a repo filter would still show site-wide totals.
    val narrowed = all.filter { filter.narrows(it) }
    val visible = narrowed.filter { filter.type.matches(it) }
    val sections = sectionNotifications(visible, filter.group)

    Box(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            // Type chips scroll horizontally; six chips with counts do not fit on phones.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(NotificationFilter.entries) { entry ->
                    val count = narrowed.count { entry.matches(it) }
                    FilterChip(
                        selected = filter.type == entry,
                        onClick = { NotificationFilterPrefs.setType(entry) },
                        label = { Text("${stringResource(entry.labelRes)} $count") },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RepoFilterField(
                    selected = filter.repo,
                    counts = repoCounts,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { sheetOpen = true }) {
                    BadgedBox(
                        badge = {
                            if (filter.activeCount > 0) {
                                Badge { Text(filter.activeCount.toString()) }
                            }
                        },
                    ) {
                        Icon(
                            Icons.Outlined.FilterList,
                            contentDescription = stringResource(R.string.m3_filter_desc),
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { vm.refresh() },
            ) {
                AsyncContent(state = state, onRetry = { vm.refresh() }) { notifications ->
                    when {
                        notifications.isEmpty() -> EmptyView(
                            text = stringResource(R.string.m3_no_notifications),
                        )

                        // Nothing matched: offer a one-tap reset so it does not look like there are no notifications.
                        visible.isEmpty() -> EmptyView(
                            text = stringResource(R.string.m3_notif_empty_filtered),
                            action = {
                                TextButton(onClick = { NotificationFilterPrefs.clearFilter() }) {
                                    Text(stringResource(R.string.m3_filter_clear))
                                }
                            },
                        )

                        else -> LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(tokens.listPadding),
                            verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                        ) {
                            sections.forEach { section ->
                                if (filter.group != NotificationGroup.NONE) {
                                    item(key = "header_${section.key}") {
                                        SectionHeader(
                                            section = section,
                                            collapsed = section.key in collapsed,
                                            onToggle = {
                                                collapsed = if (section.key in collapsed) {
                                                    collapsed - section.key
                                                } else {
                                                    collapsed + section.key
                                                }
                                            },
                                        )
                                    }
                                }
                                if (section.key !in collapsed) {
                                    items(items = section.items, key = { it.id }) { item ->
                                        val target = parseSubjectTarget(item.subject?.url)
                                        NotificationRow(
                                            item = item,
                                            selected = item.id == selectedId,
                                            startAction = swipeStart,
                                            endAction = swipeEnd,
                                            ignored = ignoredMap[item.id],
                                            onMenuOpen = { vm.loadSubscription(item.id) },
                                            onSetIgnored = { vm.setIgnored(item.id, it) },
                                            onSwipe = { action ->
                                                when (action) {
                                                    SwipeAction.MARK_READ -> vm.markAsRead(item)
                                                    SwipeAction.MARK_UNREAD -> vm.markAsUnread(item)
                                                    SwipeAction.NONE -> Unit
                                                }
                                            },
                                            onClick = {
                                                // Opening a notification marks it read.
                                                vm.markAsRead(item)
                                                when {
                                                    target == null -> Unit
                                                    target.kind in SUBJECT_KINDS -> onOpen(item, target)
                                                    else -> openOnGithub(context, item)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state is LoadState.Ready) {
            // Siblings so the "mark all read" FAB stays put when the pill fades in above it.
            AnimatedVisibility(
                visible = showBackToTop,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 86.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(
                        Icons.Outlined.ExpandLess,
                        contentDescription = stringResource(R.string.m3_back_to_top),
                    )
                }
            }
            FloatingActionButton(
                onClick = { vm.markAllAsRead() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp),
            ) {
                Icon(
                    Icons.Outlined.DoneAll,
                    contentDescription = stringResource(R.string.m3_mark_all_read),
                )
            }
        }
    }

    if (sheetOpen) {
        NotificationFilterSheet(onDismiss = { sheetOpen = false })
    }
}

/** Thread kinds that have a native detail screen. */
private val SUBJECT_KINDS = setOf("issues", "pulls", "discussions", "commits", "releases")

/**
 * `https://api.github.com/repos/{owner}/{repo}/{kind}/{number}` -> [SubjectTarget].
 * PR notifications use `/pulls/{n}`, but the issues endpoint serves both, so both map to the issue
 * screen. Commit notifications are `/commits/{sha}` with the sha in [SubjectTarget.ref].
 */
private fun parseSubjectTarget(apiUrl: String?): SubjectTarget? {
    if (apiUrl.isNullOrBlank()) return null
    val parts = apiUrl.removeSuffix("/").split("/")
    val reposIndex = parts.indexOf("repos")
    if (reposIndex < 0 || parts.size < reposIndex + 4) return null
    val kind = parts[reposIndex + 3]
    val tail = parts.getOrNull(reposIndex + 4)
    return SubjectTarget(
        owner = parts[reposIndex + 1],
        repo = parts[reposIndex + 2],
        kind = kind,
        number = tail?.toIntOrNull(),
        ref = tail?.takeIf { kind == "commits" && it.toIntOrNull() == null },
    )
}

/** The thread a notification points at (issue / PR / discussion / commit ...). */
private data class SubjectTarget(
    val owner: String,
    val repo: String,
    val kind: String,
    val number: Int?,
    val ref: String? = null,
) {
    fun key(): String = "$kind/$owner/$repo/${number ?: ref}"
}

/**
 * Repo filter: the whole field opens the menu.
 * `matchParentSize()` takes no part in the parent's measurement, so an earlier
 * `Box { field(matchParentSize) + DropdownMenu + click layer(matchParentSize) }` measured 0x0 and
 * was unclickable; now the field sizes the Box and the click layer overlays it.
 */
@Composable
private fun RepoFilterField(
    selected: String?,
    counts: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.padding(end = 4.dp)) {
        OutlinedTextField(
            value = selected ?: stringResource(R.string.m3_notif_all_repos),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
            trailingIcon = {
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.m3_notif_filter_repo),
                )
            },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.m3_notif_all_repos)) },
                trailingIcon = { Text(counts.values.sum().toString()) },
                onClick = {
                    NotificationFilterPrefs.setRepo(null)
                    expanded = false
                },
            )
            counts.forEach { (repoName, count) ->
                DropdownMenuItem(
                    text = { Text(repoName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { Text(count.toString()) },
                    onClick = {
                        NotificationFilterPrefs.setRepo(repoName)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    section: NotificationSection,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val tokens = LocalTokens.current
    val title = section.repoName
        ?: section.bucket?.let { stringResource(it.labelRes) }
        ?: stringResource(R.string.m3_notif_no_repo)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.cardShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = section.items.size.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun NotificationRow(
    item: Notification,
    selected: Boolean,
    startAction: SwipeAction,
    endAction: SwipeAction,
    ignored: Boolean?,
    onSwipe: (SwipeAction) -> Unit,
    onMenuOpen: () -> Unit,
    onSetIgnored: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onSwipe(startAction)
                SwipeToDismissBoxValue.EndToStart -> onSwipe(endAction)
                else -> Unit
            }
            // never actually dismiss: the row snaps back after the action
            false
        },
    )
    // Which of the two configured actions belongs to the direction being dragged.
    val action = when (dismissState.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> startAction
        SwipeToDismissBoxValue.EndToStart -> endAction
        else -> null
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            if (action != null && action != SwipeAction.NONE) {
                val (icon, container, onContainer) = when (action) {
                    SwipeAction.MARK_READ -> Triple(
                        Icons.Outlined.DoneAll,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    )

                    SwipeAction.MARK_UNREAD -> Triple(
                        Icons.Outlined.MarkEmailUnread,
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    )

                    SwipeAction.NONE -> Triple(
                        Icons.Outlined.Block,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val atStart = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(tokens.cardShape)
                        .background(container)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = if (atStart) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = stringResource(action.labelRes), tint = onContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(action.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer,
                    )
                }
            }
        },
    ) {
        NotificationCard(
            item = item,
            selected = selected,
            ignored = ignored,
            onMenuOpen = onMenuOpen,
            onSetIgnored = onSetIgnored,
            onClick = onClick,
        )
    }
}

@Composable
private fun NotificationCard(
    item: Notification,
    selected: Boolean,
    ignored: Boolean?,
    onMenuOpen: () -> Unit,
    onSetIgnored: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val unread = item.unread
    val tokens = LocalTokens.current
    val container = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        unread -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = tokens.cardShape,
    ) {
        Row(
            modifier = Modifier.padding(tokens.cardPadding),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(item.repository?.owner?.avatarUrl, size = tokens.avatarSize)
            Spacer(Modifier.width(if (tokens.compact) 8.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.subject?.title ?: stringResource(R.string.m3_notification),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.repoFullName.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.subject?.type?.let { Pill(it) }
                    MetaText(relativeTime(item.updatedAt))
                    if (ignored == true) {
                        Icon(
                            Icons.Outlined.VolumeOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Mute / unmute this thread on GitHub (PUT .../subscription {ignored}).
            Box {
                IconButton(
                    onClick = {
                        onMenuOpen()
                        menu = true
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.m3_more),
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val muted = ignored == true
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (muted) R.string.m3_notif_unmute else R.string.m3_notif_mute
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (muted) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menu = false
                            onSetIgnored(!muted)
                        },
                    )
                }
            }
        }
    }
}

/** Thread kinds with no native screen: rewrite the api url to the web url and let the link handler take it. */
private fun openOnGithub(context: android.content.Context, item: Notification) {
    val api = item.subject?.url ?: return
    val html = api.replace("https://api.github.com/repos/", "https://github.com/")
    runCatching { SchemeParser.launchUri(context, Uri.parse(html)) }
}
