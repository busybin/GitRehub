package io.github.busybin.gitrehub.compose.repos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.SkeletonList
import io.github.busybin.gitrehub.compose.common.UiTokens
import io.github.busybin.gitrehub.core.model.StatsOutcome
import io.github.busybin.gitrehub.core.model.WeekActivity
import io.github.busybin.gitrehub.compose.feed.EventCard
import io.github.busybin.gitrehub.compose.feed.FeedTarget
import io.github.busybin.gitrehub.compose.feed.feedTarget
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Repo activity page, matching github.com/{owner}/{repo}/activity in five blocks:
 *
 *  1. Events: repo event timeline (`repos/{o}/{r}/events`, push/branch/tag/PR/issue/release).
 *  2. Commits: weekly commit bars (`stats/commit_activity`), with a month axis and per-week detail.
 *  3. Contributors: per-person weekly commits/additions/deletions (`stats/contributors`).
 *  4. Code frequency: weekly added/deleted lines (`stats/code_frequency`).
 *  5. Punch card: commits per weekday and hour (`stats/punch_card`).
 *
 * The last four may still be computing on GitHub's side (HTTP 202); RepoActivityViewModel retries,
 * and the UI explains it with [ComputingState] instead of treating 202 as "no activity".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoActivityScreen(
    owner: String,
    repo: String,
    onOpenRepo: (String) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onOpenIssue: (String, String, Int) -> Unit = { _, _, _ -> },
    onOpenReleases: (String, String) -> Unit = { _, _ -> },
    /** Defaults to the events tab; smoke tests and future deep links can pick another. */
    initialTab: InsightsTab = InsightsTab.EVENTS,
) {
    val vm: RepoActivityViewModel = viewModel()
    val eventsVm: RepoEventsViewModel = viewModel()
    val tabs = InsightsTab.entries
    var tabIndex by rememberSaveable { mutableIntStateOf(tabs.indexOf(initialTab).coerceAtLeast(0)) }

    LaunchedEffect(owner, repo) { vm.start(owner, repo) }
    LaunchedEffect(owner, repo, tabIndex) {
        val tab = tabs[tabIndex]
        if (tab == InsightsTab.EVENTS) eventsVm.start(owner, repo) else vm.load(tab)
    }

    Column(Modifier.fillMaxSize()) {
        SecondaryScrollableTabRow(
            selectedTabIndex = tabIndex,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(stringResource(tab.labelRes), maxLines = 1) },
                )
            }
        }
        when (tabs[tabIndex]) {
            InsightsTab.EVENTS -> RepoEventsTab(
                vm = eventsVm,
                onOpenRepo = onOpenRepo,
                onOpenUser = onOpenUser,
                onOpenIssue = onOpenIssue,
                onOpenReleases = onOpenReleases,
            )

            InsightsTab.COMMITS -> CommitActivityTab(vm)
            InsightsTab.CONTRIBUTORS -> ContributorsTab(vm, onOpenUser)
            InsightsTab.FREQUENCY -> CodeFrequencyTab(vm)
            InsightsTab.PUNCH_CARD -> PunchCardTab(vm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoEventsTab(
    vm: RepoEventsViewModel,
    onOpenRepo: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenIssue: (String, String, Int) -> Unit,
    onOpenReleases: (String, String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val tokens = LocalTokens.current

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

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }) {
            AsyncContent(
                state = state,
                onRetry = { vm.refresh() },
                loading = { SkeletonList(rows = 6) },
            ) { events ->
                if (events.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.m3_activity_events_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.m3_activity_events_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    return@AsyncContent
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(tokens.listPadding),
                    verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                ) {
                    items(events, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            tokens = tokens,
                            isRead = false,
                            onOpenUser = onOpenUser,
                            onOpenRepo = onOpenRepo,
                            onOpen = { clicked ->
                                when (val target = clicked.feedTarget()) {
                                    is FeedTarget.ForkedRepo -> onOpenRepo(target.fullName)
                                    is FeedTarget.Issue ->
                                        onOpenIssue(target.owner, target.repo, target.number)

                                    is FeedTarget.Releases -> onOpenReleases(target.owner, target.repo)
                                    is FeedTarget.Repo -> onOpenRepo(target.fullName)
                                    is FeedTarget.User -> onOpenUser(target.login)
                                    null -> Unit
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
}

@Composable
private fun CommitActivityTab(vm: RepoActivityViewModel) {
    val state by vm.activity.collectAsStateWithLifecycle()
    val tokens = LocalTokens.current
    AsyncContent(
        state = state,
        onRetry = { vm.load(InsightsTab.COMMITS, force = true) },
    ) { outcome ->
        when (outcome) {
            is StatsOutcome.Computing -> ComputingState { vm.load(InsightsTab.COMMITS, force = true) }
            is StatsOutcome.Data -> CommitActivityContent(outcome.value, tokens)
        }
    }
}

@Composable
private fun CommitActivityContent(weeks: List<WeekActivity>, tokens: UiTokens) {
    val scroll = rememberScrollState()
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val locale = LocalConfiguration.current.locales[0]
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tokens.listPadding),
    ) {
        Text(stringResource(R.string.m3_activity_title_commits), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.m3_activity_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.m3_activity_total, weeks.sumOf { it.total }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        if (weeks.isEmpty() || weeks.all { it.total == 0 }) {
            Text(
                text = stringResource(R.string.m3_activity_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        val max = weeks.maxOf { it.total }.coerceAtLeast(1)
        val barWidth = 10.dp
        val barGap = 3.dp
        val chartHeight = (140 * tokens.scale).dp

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.horizontalScroll(scroll).padding(14.dp)) {
                // Month axis: one cell per month, width = weeks in it x bar width, so labels align with bars.
                Row {
                    monthSpans(weeks, monthFormatter).forEach { span ->
                        Box(Modifier.width((barWidth + barGap) * span.weeks)) {
                            Text(
                                text = span.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(barGap), verticalAlignment = Alignment.Bottom) {
                    weeks.forEachIndexed { index, week ->
                        val fraction = week.total.toFloat() / max
                        val height = (chartHeight * fraction)
                            .coerceAtLeast(if (week.total > 0) 3.dp else 4.dp)
                        Box(
                            modifier = Modifier
                                .width(barWidth)
                                .height(chartHeight)
                                .clickable { selected = if (selected == index) -1 else index },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(height)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            selected == index -> MaterialTheme.colorScheme.tertiary
                                            week.total > 0 -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                    ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        if (selected in weeks.indices) {
            Spacer(Modifier.height(14.dp))
            WeekDetailCard(
                week = weeks[selected],
                dateFormatter = dateFormatter,
                tokens = tokens,
            )
        }
    }
}

/** How many bars one month takes in a week row, plus its short name (for the month axis). */
private data class MonthSpan(val label: String, val weeks: Int)

private fun monthSpans(weeks: List<WeekActivity>, formatter: DateTimeFormatter): List<MonthSpan> {
    val spans = mutableListOf<MonthSpan>()
    weeks.forEach { week ->
        val label = formatter.format(YearMonth.from(weekStart(week.weekEpochSeconds)))
        val last = spans.lastOrNull()
        if (last == null || last.label != label) {
            spans += MonthSpan(label, 1)
        } else {
            spans[spans.lastIndex] = last.copy(weeks = last.weeks + 1)
        }
    }
    return spans
}

/** The seven days of a week (shown under the chart after tapping a bar). */
@Composable
private fun WeekDetailCard(
    week: WeekActivity,
    dateFormatter: DateTimeFormatter,
    tokens: UiTokens,
) {
    val locale = LocalConfiguration.current.locales[0]
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(tokens.cardPadding)) {
            Text(
                text = stringResource(R.string.m3_activity_week_of, dateFormatter.format(weekStart(week.weekEpochSeconds))),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.m3_activity_total, week.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (week.days.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val dayMax = week.days.max().coerceAtLeast(1)
                val dayChartHeight = (46 * tokens.scale).dp
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    week.days.forEachIndexed { index, count ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(3.dp))
                            Box(
                                Modifier
                                    .width(16.dp)
                                    .height(dayChartHeight),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(
                                            (dayChartHeight * (count.toFloat() / dayMax))
                                                .coerceAtLeast(if (count > 0) 3.dp else 2.dp)
                                        )
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (count > 0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                        ),
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = DayOfWeek.SUNDAY.plus(index.toLong())
                                    .getDisplayName(TextStyle.SHORT, locale),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 202 from `stats/...`: GitHub is still computing in the background. */
@Composable
internal fun ComputingState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.m3_insights_computing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.m3_retry)) }
    }
}

/** Start of a week from `stats/commit_activity` (GitHub gives Sunday 00:00 UTC as epoch seconds). */
internal fun weekStart(epochSeconds: Long): LocalDate =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate()
