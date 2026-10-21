package io.github.busybin.gitrehub.compose.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.data.ContributionDay
import io.github.busybin.gitrehub.compose.data.Contributions
import io.github.busybin.gitrehub.compose.data.ContributionsRepository

private sealed interface ContribState {
    data object Loading : ContribState
    data object Failed : ContribState
    data class Ready(val value: Contributions, val refreshing: Boolean) : ContribState
}

/**
 * The GitHub-style contribution heat map for the "me" page.
 * Reads the disk cache synchronously first, then refetches in the background once it is older
 * than [ContributionsRepository.FRESH_MILLIS]; a failed refetch keeps showing the cache.
 */
@Composable
fun ContributionsSection(login: String?) {
    var state by remember(login) {
        mutableStateOf<ContribState>(
            ContributionsRepository.cached(login)
                ?.let { ContribState.Ready(it.data, refreshing = false) }
                ?: ContribState.Loading,
        )
    }

    LaunchedEffect(login) {
        if (login.isNullOrBlank()) {
            state = ContribState.Failed
            return@LaunchedEffect
        }
        val cache = ContributionsRepository.cached(login)
        if (cache != null && System.currentTimeMillis() - cache.at < ContributionsRepository.FRESH_MILLIS) {
            return@LaunchedEffect
        }
        // Draw the cache first (the spinner shows a refresh is running); only else the loading state
        state = cache?.let { ContribState.Ready(it.data, refreshing = true) } ?: ContribState.Loading
        val data = runCatching { ContributionsRepository.load(login) }.getOrNull()
        state = when {
            data != null -> ContribState.Ready(data, refreshing = false)
            cache != null -> ContribState.Ready(cache.data, refreshing = false)
            else -> ContribState.Failed
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.m3_contributions_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val shown = state
                if (shown is ContribState.Ready && shown.refreshing) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            when (val s = state) {
                ContribState.Loading -> Box(
                    Modifier.fillMaxWidth().height(88.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }

                ContribState.Failed -> Text(
                    text = stringResource(R.string.m3_contributions_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is ContribState.Ready -> {
                    Text(
                        text = stringResource(R.string.m3_contributions, s.value.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    ContributionGrid(s.value.days)
                    Spacer(Modifier.height(8.dp))
                    ContributionLegend()
                }
            }
        }
    }
}

@Composable
private fun ContributionGrid(days: List<ContributionDay>) {
    if (days.isEmpty()) return
    val byDate = remember(days) { days.associateBy { it.date } }
    val first = days.first().date
    val last = days.last().date
    // One column per week, starting on the Sunday of the first cell's week (as GitHub does)
    val gridStart = first.minusDays((first.dayOfWeek.value % 7).toLong())
    val weeks = ((last.toEpochDay() - gridStart.toEpochDay()) / 7).toInt() + 1

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 2.dp
        val totalGap = gap * (weeks - 1).coerceAtLeast(0)
        val cell: Dp = ((maxWidth - totalGap) / weeks.coerceAtLeast(1).toFloat()).coerceAtLeast(3.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(weeks) { week ->
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(7) { dayOfWeek ->
                        val date = gridStart.plusDays((week * 7 + dayOfWeek).toLong())
                        val level = byDate[date]?.level
                        Box(
                            Modifier
                                .size(cell)
                                .clip(RoundedCornerShape(2.dp))
                                .background(levelColor(level)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributionLegend() {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.m3_contribution_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        repeat(5) { level ->
            Box(
                Modifier
                    .size(11.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(levelColor(level)),
            )
            Spacer(Modifier.width(3.dp))
        }
        Spacer(Modifier.width(3.dp))
        Text(
            text = stringResource(R.string.m3_contribution_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Square colour for a contribution level (null = a day outside the calendar range). */
@Composable
private fun levelColor(level: Int?): androidx.compose.ui.graphics.Color {
    val primary = MaterialTheme.colorScheme.primary
    return when (level) {
        null -> androidx.compose.ui.graphics.Color.Transparent
        0 -> MaterialTheme.colorScheme.surfaceContainerHighest
        1 -> primary.copy(alpha = 0.28f)
        2 -> primary.copy(alpha = 0.50f)
        3 -> primary.copy(alpha = 0.74f)
        else -> primary
    }
}
