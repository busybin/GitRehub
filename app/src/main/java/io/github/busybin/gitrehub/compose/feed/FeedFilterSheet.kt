package io.github.busybin.gitrehub.compose.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.R

/**
 * Filter entry point in the feed top bar: a funnel icon with a badge. It only opens the sheet;
 * the host ([io.github.busybin.gitrehub.compose.FaApp]) renders it outside the top bar.
 *
 * Trap: `ModalBottomSheet` must not live in a Scaffold top bar, which is measured by its own
 * `SubcomposeLayout` and crashes on window-attached components.
 */
@Composable
fun FeedFilterAction(onOpen: () -> Unit) {
    val filter by FeedPrefs.filter.collectAsStateWithLifecycle()

    IconButton(onClick = onOpen) {
        BadgedBox(
            badge = {
                if (filter.activeCount > 0) {
                    Badge { Text(filter.activeCount.toString()) }
                }
            },
        ) {
            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.m3_filter_desc))
        }
    }
}

/** The filter sheet with its [ModalBottomSheet] shell; must live in the Scaffold content, not the top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedFilterSheet(onDismiss: () -> Unit) {
    val filter by FeedPrefs.filter.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        FeedFilterSheetContent(filter = filter, onDismiss = onDismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedFilterSheetContent(
    filter: FeedFilterState,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 18.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.m3_filter_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { FeedPrefs.clearFilter() },
                enabled = !filter.isDefault,
            ) { Text(stringResource(R.string.m3_filter_clear)) }
        }
        Text(
            text = stringResource(R.string.m3_filter_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(stringResource(R.string.m3_filter_section_events))
        ChipFlow(FeedEventGroup.entries, filter.groups::contains, FeedEventGroup::labelRes) {
            FeedPrefs.toggleGroup(it)
        }

        SectionTitle(stringResource(R.string.m3_filter_section_time))
        ChipFlow(FeedTimeRange.entries, { it == filter.timeRange }, FeedTimeRange::labelRes) {
            FeedPrefs.setTimeRange(it)
        }

        SectionTitle(stringResource(R.string.m3_filter_section_source))
        ChipFlow(FeedSource.entries, { it == filter.source }, FeedSource::labelRes) {
            FeedPrefs.setSource(it)
        }

        SectionTitle(stringResource(R.string.m3_filter_section_read))
        ChipFlow(FeedReadFilter.entries, { it == filter.readFilter }, FeedReadFilter::labelRes) {
            FeedPrefs.setReadFilter(it)
        }

        SectionTitle(stringResource(R.string.m3_filter_section_order))
        ChipFlow(FeedOrder.entries, { it == filter.order }, FeedOrder::labelRes) {
            FeedPrefs.setOrder(it)
        }

        SectionTitle(stringResource(R.string.m3_filter_section_other))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.m3_filter_hide_bots),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = filter.hideBots, onCheckedChange = { FeedPrefs.setHideBots(it) })
        }

        SectionTitle(stringResource(R.string.m3_filter_keywords))
        Hint(stringResource(R.string.m3_filter_keywords_hint))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = joinKeywords(filter.keywords),
            onValueChange = { FeedPrefs.setBlocked(it) },
            placeholder = { Text(stringResource(R.string.m3_filter_keywords_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_done)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Auto-wrapping row of single/multi-select chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipFlow(
    options: List<T>,
    isSelected: (T) -> Boolean,
    labelRes: (T) -> Int,
    onClick: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = isSelected(option),
                onClick = { onClick(option) },
                label = { Text(stringResource(labelRes(option))) },
            )
        }
    }
}
