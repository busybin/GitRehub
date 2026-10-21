package io.github.busybin.gitrehub.compose.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LinkCopyDialog
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.helper.BrowserLauncher

private const val TOKEN_SCOPES = "repo,user,notifications,read:org,gist,workflow,read:packages"

/** [LoginScreen] doubles as "add another account" (reached from the account switcher). */
enum class LoginMode { SIGN_IN, ADD_ACCOUNT }

/**
 * Token based sign-in.
 *
 * The OAuth web flow of the legacy app needs an OAuth App owned by us (client id/secret); until that
 * exists this screen gets you in with a personal access token and stays fully local.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    mode: LoginMode = LoginMode.SIGN_IN,
    onOpenDeviceLogin: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val vm: LoginViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var token by rememberSaveable { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    // BrowserLauncher returns false when it cannot open; fall back to a copyable link
    var failedUrl by remember { mutableStateOf<String?>(null) }
    val adding = mode == LoginMode.ADD_ACCOUNT

    if (state is LoadState.Ready) {
        LaunchedEffect(state) { onLoggedIn() }
    }

    var choice by remember { mutableStateOf<Int>(0) } // 0 = choice screen, 1 = PAT
    // Back inside the PAT form returns to the choice screen, not to the previous route
    BackHandler(enabled = choice == 1) { choice = 0 }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (adding) stringResource(R.string.m3_title_add_account) else "GitRehub",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (adding) stringResource(R.string.m3_login_subtitle_add)
                else stringResource(R.string.m3_login_subtitle),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            if (choice == 0) {
                if (onOpenDeviceLogin != null) {
                    Button(
                        onClick = onOpenDeviceLogin,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.m3_login_method_device))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.m3_login_device_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { choice = 1 },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.m3_login_method_pat))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.m3_login_scopes_add),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it.trim()
                        vm.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.m3_login_pat_label)) },
                    singleLine = true,
                    visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (revealed) R.string.m3_hide else R.string.m3_show
                                ),
                            )
                        }
                    },
                )

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.login(token) },
                    enabled = state !is LoadState.Loading && token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state is LoadState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        when {
                            state is LoadState.Loading -> stringResource(R.string.m3_login_signing_in)
                            adding -> stringResource(R.string.m3_login_button_add)
                            else -> stringResource(R.string.m3_login_button)
                        }
                    )
                }

                val failure = state as? LoadState.Failure
                if (failure != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = failure.message.render(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = {
                        val url = "https://github.com/settings/tokens/new" +
                            "?description=GitRehub&scopes=$TOKEN_SCOPES"
                        if (!BrowserLauncher.open(context, url)) failedUrl = url
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.m3_login_gen_token))
                }

                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { choice = 0 }) {
                    Text(stringResource(R.string.m3_login_back_to_methods))
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.m3_login_scopes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        failedUrl?.let { url ->
            LinkCopyDialog(url = url, onDismiss = { failedUrl = null })
        }
    }
}
