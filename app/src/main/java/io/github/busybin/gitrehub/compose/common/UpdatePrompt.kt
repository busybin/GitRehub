package io.github.busybin.gitrehub.compose.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.busybin.gitrehub.BuildConfig
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.data.UpdateChecker
import io.github.busybin.gitrehub.compose.data.UpdatePrefs
import io.github.busybin.gitrehub.helper.BrowserLauncher

/**
 * Silently checks for an update in the background and shows the dialog only once: it appears when a
 * newer version exists and has not been prompted for yet, the version is recorded in [UpdatePrefs],
 * and it never nags again (the manual "check for updates" in settings still works).
 *
 * [enabled] is "signed in"; there is no point checking before that.
 */
@Composable
fun UpdatePromptHost(enabled: Boolean) {
    val context = LocalContext.current
    var available by remember { mutableStateOf<UpdateChecker.Result.Available?>(null) }
    val auto by UpdatePrefs.auto.collectAsState()

    // Off by default (see UpdatePrefs.auto): no request goes out until it is enabled in settings.
    LaunchedEffect(enabled, auto) {
        if (!enabled || !auto) return@LaunchedEffect
        val outcome = runCatching { UpdateChecker.check(BuildConfig.VERSION_CODE) }.getOrNull()
        if (outcome is UpdateChecker.Result.Available) {
            UpdatePrefs.recordKnown(outcome.version)
            if (UpdatePrefs.shouldPrompt(BuildConfig.VERSION_CODE) != null) {
                available = outcome
            }
        }
    }

    val update = available ?: return
    AlertDialog(
        onDismissRequest = {
            UpdatePrefs.markPrompted(update.version)
            available = null
        },
        title = { Text(stringResource(R.string.m3_update_prompt_title, update.tag)) },
        text = {
            Text(stringResource(R.string.m3_update_prompt_message, BuildConfig.VERSION_NAME))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    UpdatePrefs.markPrompted(update.version)
                    available = null
                    BrowserLauncher.open(context, update.url)
                },
            ) { Text(stringResource(R.string.m3_update_download)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    UpdatePrefs.markPrompted(update.version)
                    available = null
                },
            ) { Text(stringResource(R.string.m3_update_later)) }
        },
    )
}
