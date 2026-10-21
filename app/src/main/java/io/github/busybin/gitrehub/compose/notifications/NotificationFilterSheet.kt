package io.github.busybin.gitrehub.compose.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
 * Notifications filter sheet. Must be hosted in the page content, not the Scaffold top bar:
 * the top bar is measured by its own `SubcomposeLayout` and crashes on window-attached components
 * like `ModalBottomSheet` (see compose/feed/FeedFilterSheet.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationFilterSheet(onDismiss: () -> Unit) {
    val filter by NotificationFilterPrefs.filter.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.m3_notif_filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { NotificationFilterPrefs.clearFilter() },
                    enabled = filter.activeCount > 0,
                ) { Text(stringResource(R.string.m3_filter_clear)) }
            }
            Text(
                text = stringResource(R.string.m3_notif_filter_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            NotifSectionTitle(stringResource(R.string.m3_notif_filter_section_group))
            NotifChipFlow(
                options = NotificationGroup.entries,
                isSelected = { it == filter.group },
                labelRes = NotificationGroup::labelRes,
            ) { NotificationFilterPrefs.setGroup(it) }

            NotifSectionTitle(stringResource(R.string.m3_notif_filter_section_reason))
            Text(
                text = stringResource(R.string.m3_notif_filter_reason_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            NotifChipFlow(
                options = NotificationReasonGroup.entries,
                isSelected = { it in filter.reasons },
                labelRes = NotificationReasonGroup::labelRes,
            ) { NotificationFilterPrefs.toggleReason(it) }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_done)) }
            }
        }
    }
}

@Composable
private fun NotifSectionTitle(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

/** Auto-wrapping row of single/multi-select chips; a copy of the one in feed/FeedFilterSheet.kt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> NotifChipFlow(
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
