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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.busybin.gitrehub.BuildConfig
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.data.UpdateChecker
import io.github.busybin.gitrehub.compose.data.UpdateConsent
import io.github.busybin.gitrehub.compose.data.UpdatePrefs
import io.github.busybin.gitrehub.helper.BrowserLauncher

/**
 * Asks once, on the first launch, whether the app may check for updates by itself; after that,
 * every launch checks once and shows the dialog when a newer release exists. Nothing is requested
 * before the question is answered, a rotation does not ask again, and the check never downloads or
 * installs anything on its own.
 *
 * [enabled] is false for builds the store manages, where the check would be meaningless.
 */
@Composable
fun UpdatePromptHost(enabled: Boolean) {
    val context = LocalContext.current
    val consent by UpdatePrefs.consent.collectAsState()

    if (enabled && consent == UpdateConsent.UNASKED) {
        AlertDialog(
            onDismissRequest = { UpdatePrefs.setConsent(UpdateConsent.DENIED) },
            title = { Text(stringResource(R.string.m3_update_consent_title)) },
            text = { Text(stringResource(R.string.m3_update_consent_message)) },
            confirmButton = {
                TextButton(onClick = { UpdatePrefs.setConsent(UpdateConsent.ALLOWED) }) {
                    Text(stringResource(R.string.m3_update_consent_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { UpdatePrefs.setConsent(UpdateConsent.DENIED) }) {
                    Text(stringResource(R.string.m3_update_consent_deny))
                }
            },
        )
        return
    }

    var available by remember { mutableStateOf<UpdateChecker.Result.Available?>(null) }
    // One check per launch: a rotation must not send the request again or reopen the dialog.
    var checkedThisLaunch by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(enabled, consent) {
        if (!enabled || consent != UpdateConsent.ALLOWED || checkedThisLaunch) return@LaunchedEffect
        checkedThisLaunch = true
        val outcome = runCatching { UpdateChecker.check(BuildConfig.VERSION_CODE) }.getOrNull()
        if (outcome is UpdateChecker.Result.Available) {
            UpdatePrefs.recordKnown(outcome.version)
            available = outcome
        }
    }

    val update = available ?: return
    AlertDialog(
        onDismissRequest = { available = null },
        title = { Text(stringResource(R.string.m3_update_prompt_title, update.tag)) },
        text = {
            Text(stringResource(R.string.m3_update_prompt_message, BuildConfig.VERSION_NAME))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    available = null
                    BrowserLauncher.open(context, update.url)
                },
            ) { Text(stringResource(R.string.m3_update_download)) }
        },
        dismissButton = {
            TextButton(onClick = { available = null }) {
                Text(stringResource(R.string.m3_update_later))
            }
        },
    )
}
