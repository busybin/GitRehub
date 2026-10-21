package io.github.busybin.gitrehub.compose.accounts

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.core.auth.StoredAccounts
import io.github.busybin.gitrehub.core.auth.StoredAccount
import kotlinx.coroutines.delay

/**
 * Account switcher: the account in use, the other stored ones, and a way to add another.
 *
 * Switching is a real switch — the token every request is sent with changes — so the caller is told
 * when it succeeded and restarts the UI (see [io.github.busybin.gitrehub.compose.common.restartModernUi]).
 */
@Composable
fun AccountsScreen(
    onAddAccount: () -> Unit,
    onSwitched: () -> Unit,
) {
    val vm: AccountsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    // Informational note; let it fade away by itself.
    LaunchedEffect(message) {
        if (message != null) {
            delay(3_000)
            vm.clearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (message != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message?.render().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        AsyncContent(state = state, onRetry = { vm.load() }) { accounts ->
            AccountsBody(
                accounts = accounts,
                busy = busy,
                onSwitch = { vm.switchTo(it, onSwitched) },
                onRemove = { vm.remove(it) },
            )
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onAddAccount,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.m3_title_add_account))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.m3_accounts_add_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountsBody(
    accounts: StoredAccounts,
    busy: Boolean,
    onSwitch: (StoredAccount) -> Unit,
    onRemove: (StoredAccount) -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<StoredAccount?>(null) }

    if (accounts.isEmpty) {
        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.m3_accounts_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (accounts.current != null) {
        SectionLabel(stringResource(R.string.m3_accounts_current))
        AccountCard(account = accounts.current, isCurrent = true, busy = busy, onSwitch = onSwitch, onRemove = null)
    }

    if (accounts.others.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        SectionLabel(stringResource(R.string.m3_accounts_tap_switch))
        accounts.others.forEach { account ->
            AccountCard(
                account = account,
                isCurrent = false,
                busy = busy,
                onSwitch = onSwitch,
                onRemove = { pendingRemoval = account },
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (accounts.enterpriseCount > 0) {
        Spacer(Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.m3_accounts_enterprise, accounts.enterpriseCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val removal = pendingRemoval
    if (removal != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.m3_accounts_remove_title)) },
            text = {
                Text(stringResource(R.string.m3_accounts_remove_message, removal.login.orEmpty()))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    onRemove(removal)
                }) { Text(stringResource(R.string.m3_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text(stringResource(R.string.m3_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun AccountCard(
    account: StoredAccount,
    isCurrent: Boolean,
    busy: Boolean,
    onSwitch: (StoredAccount) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrent && !busy) { onSwitch(account) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(account.avatarUrl, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = account.name ?: account.login.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = account.login?.let { "@$it" } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCurrent) {
                Pill(stringResource(R.string.m3_accounts_current_pill))
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(
                            R.string.m3_accounts_remove_desc,
                            account.login.orEmpty(),
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
