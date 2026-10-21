package io.github.busybin.gitrehub.compose.repos

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.UiTokens
import io.github.busybin.gitrehub.compose.common.compactNumber
import io.github.busybin.gitrehub.core.model.CodeFrequency
import io.github.busybin.gitrehub.core.model.ContributorMetric
import io.github.busybin.gitrehub.core.model.ContributorStat
import io.github.busybin.gitrehub.core.model.metricOf
import io.github.busybin.gitrehub.core.model.PunchCard
import io.github.busybin.gitrehub.core.model.StatsOutcome
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

@Composable
internal fun ContributorsTab(vm: RepoActivityViewModel, onOpenUser: (String) -> Unit) {
    val state by vm.contributors.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    val tokens = LocalTokens.current

    AsyncContent(
        state = state,
        onRetry = { vm.load(InsightsTab.CONTRIBUTORS, force = true) },
    ) { outcome ->
        when (outcome) {
            is StatsOutcome.Computing -> ComputingState { vm.load(InsightsTab.CONTRIBUTORS, force = true) }
            is StatsOutcome.Data -> {
                val contributors = outcome.value
                if (contributors.isEmpty()) {
                    EmptyInsight()
                    return@AsyncContent
                }
                // The sort metric is a UI choice: re-sort only, no refetch.
                val sorted = remember(contributors, metric) {
                    contributors.sortedByDescending { it.metricOf(metric) }
                }
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.listPadding, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContributorMetric.entries.forEach { option ->
                            FilterChip(
                                selected = metric == option,
                                onClick = { vm.setMetric(option) },
                                label = { Text(stringResource(option.labelRes)) },
                            )
                        }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = tokens.listPadding,
                            end = tokens.listPadding,
                            bottom = tokens.listPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
                    ) {
                        items(sorted, key = { it.login }) { contributor ->
                            ContributorRow(
                                contributor = contributor,
                                metric = metric,
                                tokens = tokens,
                                onOpenUser = onOpenUser,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ContributorMetric.labelRes: Int
    get() = when (this) {
        ContributorMetric.COMMITS -> R.string.m3_contrib_metric_commits
        ContributorMetric.ADDITIONS -> R.string.m3_contrib_metric_additions
        ContributorMetric.DELETIONS -> R.string.m3_contrib_metric_deletions
    }

@Composable
private fun ContributorRow(
    contributor: ContributorStat,
    metric: ContributorMetric,
    tokens: UiTokens,
    onOpenUser: (String) -> Unit,
) {
    val color = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUser(contributor.login) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = tokens.cardShape,
    ) {
        Column(Modifier.padding(tokens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(contributor.avatarUrl, size = tokens.avatarSize)
                Spacer(Modifier.width(if (tokens.compact) 8.dp else 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = contributor.login,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            when (metric) {
                                ContributorMetric.COMMITS -> R.string.m3_contrib_commits_total
                                ContributorMetric.ADDITIONS -> R.string.m3_contrib_additions_total
                                ContributorMetric.DELETIONS -> R.string.m3_contrib_deletions_total
                            },
                            compactNumber(contributor.metricOf(metric)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // One mini bar per week of the year, like GitHub's contributor graph.
            Spacer(Modifier.height(8.dp))
            Sparkline(
                values = contributor.weeks.map { it.metricOf(metric) },
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
            )
        }
    }
}

/** A series drawn as a minimal bar chart (52 small bars) with Canvas instead of 52 Boxes. */
@Composable
private fun Sparkline(values: List<Long>, color: Color, modifier: Modifier) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val step = size.width / values.size
        val barWidth = (step * 0.7f).coerceAtLeast(1f)
        values.forEachIndexed { index, value ->
            if (value <= 0L) return@forEachIndexed
            val barHeight = max(size.height * (value / max), 1f)
            drawRect(
                color = color,
                topLeft = Offset(index * step, size.height - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

@Composable
internal fun CodeFrequencyTab(vm: RepoActivityViewModel) {
    val state by vm.frequency.collectAsStateWithLifecycle()
    val tokens = LocalTokens.current

    AsyncContent(
        state = state,
        onRetry = { vm.load(InsightsTab.FREQUENCY, force = true) },
    ) { outcome ->
        when (outcome) {
            is StatsOutcome.Computing -> ComputingState { vm.load(InsightsTab.FREQUENCY, force = true) }
            is StatsOutcome.Data -> {
                val weeks = outcome.value
                if (weeks.isEmpty() || weeks.all { it.additions == 0L && it.deletions == 0L }) {
                    EmptyInsight()
                    return@AsyncContent
                }
                val added = weeks.sumOf { it.additions }
                val deleted = weeks.sumOf { abs(it.deletions) }
                val up = MaterialTheme.colorScheme.primary
                val down = MaterialTheme.colorScheme.error
                val axis = MaterialTheme.colorScheme.outlineVariant

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(tokens.listPadding),
                ) {
                    Text(
                        text = stringResource(R.string.m3_code_frequency_summary, compactNumber(added), compactNumber(deleted)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        DivergingBars(
                            weeks = weeks,
                            up = up,
                            down = down,
                            axis = axis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((180 * tokens.scale).dp)
                                .padding(14.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot(up, stringResource(R.string.m3_contrib_metric_additions))
                        LegendDot(down, stringResource(R.string.m3_contrib_metric_deletions))
                    }
                }
            }
        }
    }
}

/** One bar per week: up = additions, down = deletions (like GitHub's code frequency chart). */
@Composable
private fun DivergingBars(
    weeks: List<CodeFrequency>,
    up: Color,
    down: Color,
    axis: Color,
    modifier: Modifier,
) {
    val maxAbs = remember(weeks) {
        weeks.maxOf { max(abs(it.additions), abs(it.deletions)) }.coerceAtLeast(1L).toFloat()
    }
    Canvas(modifier) {
        val step = size.width / weeks.size
        val barWidth = (step * 0.66f).coerceAtLeast(1f)
        val middle = size.height / 2f
        val unit = middle / maxAbs
        weeks.forEachIndexed { index, week ->
            val additions = week.additions.coerceAtLeast(0L) * unit
            val deletions = abs(week.deletions) * unit
            if (additions > 0f) {
                drawRect(up, Offset(index * step, middle - additions), Size(barWidth, additions))
            }
            if (deletions > 0f) {
                drawRect(down, Offset(index * step, middle), Size(barWidth, deletions))
            }
        }
        drawLine(axis, Offset(0f, middle), Offset(size.width, middle), strokeWidth = 1f)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PunchCardTab(vm: RepoActivityViewModel) {
    val state by vm.punchCard.collectAsStateWithLifecycle()
    val tokens = LocalTokens.current
    val locale = LocalConfiguration.current.locales[0]

    AsyncContent(
        state = state,
        onRetry = { vm.load(InsightsTab.PUNCH_CARD, force = true) },
    ) { outcome ->
        when (outcome) {
            is StatsOutcome.Computing -> ComputingState { vm.load(InsightsTab.PUNCH_CARD, force = true) }
            is StatsOutcome.Data -> {
                val card = outcome.value
                if (card.total == 0) {
                    EmptyInsight()
                    return@AsyncContent
                }
                val color = MaterialTheme.colorScheme.primary
                val axis = MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(tokens.listPadding),
                ) {
                    Text(
                        text = stringResource(R.string.m3_activity_total, card.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    card.peak?.let { (day, hour, _) ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.m3_punch_card_summary,
                                dayName(day, locale),
                                String.format(Locale.US, "%02d:00", hour),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row {
                                Column(
                                    modifier = Modifier.height((168 * tokens.scale).dp),
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    repeat(PunchCard.DAYS) { day ->
                                        Text(
                                            text = dayName(day, locale),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = axis,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                PunchGrid(
                                    card = card,
                                    color = color,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((168 * tokens.scale).dp),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf(0, 6, 12, 18, 23).forEach { hour ->
                                    Text(
                                        text = String.format(Locale.US, "%02d", hour),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = axis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 7x24 dots: the radius follows the commit count, as GitHub's punch card does. */
@Composable
private fun PunchGrid(card: PunchCard, color: Color, modifier: Modifier) {
    val busiest = card.busiest.coerceAtLeast(1)
    Canvas(modifier) {
        val cellWidth = size.width / PunchCard.HOURS
        val cellHeight = size.height / PunchCard.DAYS
        val maxRadius = minOf(cellWidth, cellHeight) / 2f
        repeat(PunchCard.DAYS) { day ->
            repeat(PunchCard.HOURS) { hour ->
                val count = card.cell(day, hour)
                val center = Offset(
                    x = hour * cellWidth + cellWidth / 2f,
                    y = day * cellHeight + cellHeight / 2f,
                )
                if (count <= 0) {
                    // A small grey dot for empty slots, otherwise the grid is invisible.
                    drawCircle(color.copy(alpha = 0.10f), radius = maxRadius * 0.18f, center = center)
                } else {
                    val fraction = sqrt(count.toFloat() / busiest)
                    drawCircle(
                        color = color.copy(alpha = (0.35f + 0.65f * fraction)),
                        radius = (maxRadius * (0.35f + 0.65f * fraction)).coerceAtLeast(maxRadius * 0.25f),
                        center = center,
                    )
                }
            }
        }
    }
}

private fun dayName(day: Int, locale: Locale): String =
    DayOfWeek.SUNDAY.plus(day.toLong()).getDisplayName(TextStyle.SHORT, locale)

/** `stats/...` returned but really has no data (different from 202 "still computing"). */
@Composable
private fun EmptyInsight() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.m3_insights_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
