package io.github.busybin.gitrehub.compose.repos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.core.model.DependabotAlert
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.model.TrafficPath
import io.github.busybin.gitrehub.core.model.TrafficReferrer
import io.github.busybin.gitrehub.core.model.TrafficSeries

/**
 * Maintainer dashboard for repositories the account can push to: 14-day traffic, where visitors came
 * from, the most visited pages, open Dependabot alerts and issues nobody touched for 90 days.
 *
 * The official mobile clients do not expose any of this; all of it is public REST, read-only.
 */
@Composable
fun RepoInsightsScreen(
    owner: String,
    repo: String,
    onOpenIssue: (String, String, Int) -> Unit = { _, _, _ -> },
) {
    val vm: InsightsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(owner, repo) { vm.load(owner, repo) }

    AsyncContent(state = state, onRetry = { vm.load(owner, repo) }) { data ->
        if (data.views == null && data.clones == null && data.referrers.isEmpty()) {
            EmptyView(stringResource(R.string.m3_insights_unavailable))
            return@AsyncContent
        }
        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            data.views?.let { views ->
                item {
                    TrafficCard(
                        title = stringResource(R.string.m3_insights_views),
                        series = views,
                        iconColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            data.clones?.let { clones ->
                item {
                    TrafficCard(
                        title = stringResource(R.string.m3_insights_clones),
                        series = clones,
                        iconColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (data.referrers.isNotEmpty()) {
                item {
                    InsightsSection(stringResource(R.string.m3_insights_referrers)) {
                        data.referrers.forEach { ReferrerRow(it) }
                    }
                }
            }
            if (data.paths.isNotEmpty()) {
                item {
                    InsightsSection(stringResource(R.string.m3_insights_paths)) {
                        data.paths.forEach { PathRow(it) }
                    }
                }
            }
            item {
                InsightsSection(stringResource(R.string.m3_insights_alerts)) {
                    when {
                        data.alertsUnavailable -> MetaText(
                            stringResource(R.string.m3_insights_alerts_unavailable)
                        )
                        data.alerts.isEmpty() -> MetaText(
                            stringResource(R.string.m3_insights_alerts_none)
                        )
                        else -> data.alerts.forEach { AlertRow(it) }
                    }
                }
            }
            item {
                InsightsSection(stringResource(R.string.m3_insights_stale)) {
                    when {
                        data.staleUnavailable -> MetaText(
                            stringResource(R.string.m3_insights_stale_unavailable)
                        )
                        data.staleIssues.isEmpty() -> MetaText(
                            stringResource(R.string.m3_insights_stale_none)
                        )
                        else -> data.staleIssues.take(20).forEach { issue ->
                            StaleIssueRow(issue) {
                                onOpenIssue(owner, repo, issue.number)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TrafficCard(title: String, series: TrafficSeries, iconColor: Color) {
    val points = series.points
    val max = points.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.m3_insights_total, series.count.toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            MetaText(stringResource(R.string.m3_insights_uniques, series.uniques.toInt()))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                points.takeLast(14).forEach { point ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight((point.count.toFloat() / max).coerceIn(0.03f, 1f))
                            .background(iconColor.copy(alpha = 0.75f), RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsSection(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ReferrerRow(referrer: TrafficReferrer) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = referrer.referrer.ifBlank { stringResource(R.string.m3_insights_direct) },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MetaText("${referrer.count} · ${referrer.uniques}")
    }
}

@Composable
private fun PathRow(path: TrafficPath) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = path.title.ifBlank { path.path },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaText(path.path, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        MetaText("${path.count} · ${path.uniques}")
    }
}

@Composable
private fun AlertRow(alert: DependabotAlert) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val color = when (alert.severity) {
            "critical", "high" -> Color(0xFFF85149)
            "medium" -> Color(0xFFD29922)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Pill(
            text = alert.severity.ifBlank { "?" },
            container = color.copy(alpha = 0.15f),
            content = color,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = alert.packageName.ifBlank { alert.summary },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (alert.summary.isNotBlank() && alert.packageName.isNotBlank()) {
                MetaText(alert.summary, maxLines = 2)
            }
        }
    }
}

@Composable
private fun StaleIssueRow(issue: Issue, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
    ) {
        Text(
            text = "#${issue.number} ${issue.title.orEmpty()}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        MetaText(io.github.busybin.gitrehub.compose.common.relativeTime(issue.updatedAt))
    }
    HorizontalDivider()
}
