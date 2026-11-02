package io.github.busybin.gitrehub.compose.issues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.core.model.CheckRun
import io.github.busybin.gitrehub.helper.BrowserLauncher

private val PASSED = Color(0xFF3FB950)
private val FAILED = Color(0xFFF85149)

/** `12 passed · 2 failed · 1 running` — the merge decision needs this right next to the button. */
@Composable
fun ChecksSummary(checks: List<CheckRun>, onClick: () -> Unit) {
    if (checks.isEmpty()) return
    val passed = checks.count { it.isDone && it.isOk }
    val failed = checks.count { it.isDone && !it.isOk }
    val running = checks.count { !it.isDone }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.m3_checks_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (passed > 0) {
            Text(
                text = stringResource(R.string.m3_checks_summary_passed, passed),
                style = MaterialTheme.typography.labelMedium,
                color = PASSED,
            )
        }
        if (failed > 0) {
            Text(
                text = stringResource(R.string.m3_checks_summary_failed, failed),
                style = MaterialTheme.typography.labelMedium,
                color = FAILED,
            )
        }
        if (running > 0) {
            Text(
                text = stringResource(R.string.m3_checks_summary_running, running),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Full list of the PR head commit's checks; tapping one opens its details on github.com. */
@Composable
fun ChecksDialog(checks: List<CheckRun>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m3_checks_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                checks.forEach { check ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !check.htmlUrl.isNullOrBlank()) {
                                check.htmlUrl?.let { BrowserLauncher.open(context, it) }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = check.name.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = check.word.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                check.isDone && check.isOk -> PASSED
                                check.isDone -> FAILED
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_close)) }
        },
    )
}
