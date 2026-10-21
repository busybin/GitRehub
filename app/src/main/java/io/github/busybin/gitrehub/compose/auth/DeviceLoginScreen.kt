package io.github.busybin.gitrehub.compose.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LinkCopyDialog
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.helper.BrowserLauncher

/**
 * Web login without a redirect: GitHub hands us a short code, the user types it in the browser, and
 * we poll in the background until the token arrives.
 *
 * Preferred over pasting a personal access token because the user never has to create/copy one, and
 * over the old redirect-based OAuth because it needs no client secret, no callback URL and — most
 * usefully — works in "add another account" mode too, since nothing leaves the app.
 */
@Composable
fun DeviceLoginScreen(
    onLoggedIn: () -> Unit,
    mode: LoginMode = LoginMode.SIGN_IN,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val vm: DeviceLoginViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    var failedUrl by remember { mutableStateOf<String?>(null) }
    val adding = mode == LoginMode.ADD_ACCOUNT
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) { vm.start() }
    // Only navigate once the app is back in the foreground: firing nav.navigate()
    // while we are still backgrounded (browser on top) crashes with
    // "Can not perform this action after onSaveInstanceState".
    LaunchedEffect(done) {
        if (!done) return@LaunchedEffect
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleOwner.lifecycle.currentStateFlow
                .first { it.isAtLeast(Lifecycle.State.RESUMED) }
        }
        onLoggedIn()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.VpnKey,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (adding) R.string.m3_device_title_add else R.string.m3_device_title_login
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.m3_device_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            when (val s = state) {
                is LoadState.Loading -> {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.m3_device_requesting), style = MaterialTheme.typography.bodyMedium)
                }

                is LoadState.Failure -> {
                    Text(
                        text = s.message.render(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.restart() }) { Text(stringResource(R.string.m3_retry)) }
                }

                is LoadState.Ready -> {
                    val challenge = s.value
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.m3_device_code_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = challenge.userCode,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp,
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(challenge.userCode))
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.m3_device_copy_code))
                                }
                                Button(
                                    onClick = {
                                        if (!BrowserLauncher.open(context, challenge.verificationUri)) {
                                            failedUrl = challenge.verificationUri
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.m3_device_open_browser))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = challenge.verificationUri,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!done) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = status.render(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.restart() }) {
                            Text(stringResource(R.string.m3_device_new_code))
                        }
                        TextButton(
                            onClick = {
                                if (!BrowserLauncher.open(context, challenge.verificationUri)) {
                                    failedUrl = challenge.verificationUri
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.m3_device_reopen))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.m3_device_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        failedUrl?.let { url ->
            LinkCopyDialog(url = url, onDismiss = { failedUrl = null })
        }
    }
}
