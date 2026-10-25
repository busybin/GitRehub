package io.github.busybin.gitrehub.compose.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.notifications.NotificationRule
import io.github.busybin.gitrehub.compose.notifications.NotificationRules

/**
 * Local mute rules: a matching notification is archived (marked read) automatically.
 * Rules are device-local; the server subscription is never touched.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationRuleSettings() {
    val rules by NotificationRules.rules.collectAsStateWithLifecycle()
    var value by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(NotificationRule.KIND_REPO) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.m3_notif_rules), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.m3_notif_rules_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (rules.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rules.forEach { rule ->
                    FilterChip(
                        selected = true,
                        onClick = { NotificationRules.remove(rule.id) },
                        label = { Text(rule.value) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.m3_notif_rule_remove),
                                modifier = Modifier.size(14.dp),
                            )
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = kind == NotificationRule.KIND_REPO,
                onClick = { kind = NotificationRule.KIND_REPO },
                label = { Text(stringResource(R.string.m3_notif_rule_repo)) },
            )
            FilterChip(
                selected = kind == NotificationRule.KIND_KEYWORD,
                onClick = { kind = NotificationRule.KIND_KEYWORD },
                label = { Text(stringResource(R.string.m3_notif_rule_keyword)) },
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.m3_notif_rule_value)) },
            trailingIcon = {
                TextButton(
                    onClick = {
                        NotificationRules.add(kind, value)
                        value = ""
                    },
                    enabled = value.isNotBlank(),
                ) { Text(stringResource(R.string.m3_notif_rule_add)) }
            },
        )
    }
}

