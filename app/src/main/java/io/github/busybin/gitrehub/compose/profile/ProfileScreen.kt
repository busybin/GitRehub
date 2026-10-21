package io.github.busybin.gitrehub.compose.profile

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.ConfirmDialog
import io.github.busybin.gitrehub.compose.common.compactNumber
import io.github.busybin.gitrehub.helper.BrowserLauncher

/**
 * The logged-in account: stats and contribution graph, plus the two account-level actions
 * (switch account / sign out) and the profile link.
 *
 * Layout: avatar/name/bio centred and the two big buttons side by side filling one row; the
 * "open profile in browser" link is centred at the bottom.
 */
@Composable
fun ProfileScreen(
    onOpenAccounts: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val vm: ProfileViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val saveResult by vm.saveResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEdit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    LaunchedEffect(saveResult) {
        when (saveResult) {
            ProfileSaveResult.SAVED -> {
                showEdit = false
                Toast.makeText(context, R.string.m3_edit_profile_saved, Toast.LENGTH_SHORT).show()
            }
            ProfileSaveResult.DENIED ->
                Toast.makeText(context, R.string.m3_edit_profile_denied, Toast.LENGTH_LONG).show()
            ProfileSaveResult.FAILED ->
                Toast.makeText(context, R.string.m3_edit_profile_failed, Toast.LENGTH_SHORT).show()
            null -> return@LaunchedEffect
        }
        vm.consumeSaveResult()
    }

    AsyncContent(state = state, onRetry = { vm.load() }) { account ->
        val user = account.toUserInfo()
        var confirmLogout by remember { mutableStateOf(false) }
        if (showEdit) {
            EditProfileDialog(
                user = user,
                saving = saving,
                onDismiss = { showEdit = false },
                onSave = { vm.save(it) },
            )
        }
        if (confirmLogout) {
            ConfirmDialog(
                title = stringResource(R.string.m3_logout_title),
                message = stringResource(R.string.m3_logout_message),
                confirmLabel = stringResource(R.string.m3_logout_confirm),
                destructive = true,
                onConfirm = {
                    confirmLogout = false
                    vm.logout(onLoggedOut)
                },
                onDismiss = { confirmLogout = false },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(6.dp))
            Avatar(user.avatarUrl, size = 96.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = user.name ?: user.login.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = user.login?.let { "@$it" } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canEdit) {
                TextButton(onClick = { showEdit = true }) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.m3_edit_profile))
                }
            }
            if (!user.bio.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = user.bio,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Stat(stringResource(R.string.m3_profile_repos), compactNumber(user.publicRepos))
                    Stat(stringResource(R.string.m3_profile_followers), compactNumber(user.followers))
                    Stat(stringResource(R.string.m3_profile_following), compactNumber(user.following))
                }
            }

            Spacer(Modifier.height(16.dp))
            // Two big buttons side by side, together filling the width.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onOpenAccounts,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.m3_switch_account)) }
                OutlinedButton(
                    onClick = { confirmLogout = true },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.m3_logout)) }
            }

            Spacer(Modifier.height(20.dp))
            ContributionsSection(login = user.login)

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Profile rows (company / location / homepage).
            if (!user.company.isNullOrBlank()) InfoRow(Icons.Outlined.Business, user.company)
            if (!user.location.isNullOrBlank()) InfoRow(Icons.Outlined.LocationOn, user.location)
            if (!user.blog.isNullOrBlank()) {
                InfoRow(Icons.Outlined.Link, user.blog, onClick = { openInBrowser(context, user.blog) })
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { openInBrowser(context, user.htmlUrl ?: "https://github.com") }) {
                Text(stringResource(R.string.m3_open_profile_browser))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

internal fun openInBrowser(context: Context, url: String) {
    BrowserLauncher.open(context, url)
}

@Composable
private fun EditProfileDialog(
    user: UserInfo,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ProfileEdit) -> Unit,
) {
    var name by remember { mutableStateOf(user.name.orEmpty()) }
    var bio by remember { mutableStateOf(user.bio.orEmpty()) }
    var company by remember { mutableStateOf(user.company.orEmpty()) }
    var location by remember { mutableStateOf(user.location.orEmpty()) }
    var blog by remember { mutableStateOf(user.blog.orEmpty()) }
    var email by remember { mutableStateOf(user.email.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.m3_edit_profile)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditField(R.string.m3_edit_profile_name, name) { name = it }
                EditField(R.string.m3_edit_profile_bio, bio, singleLine = false) { bio = it }
                EditField(R.string.m3_edit_profile_company, company) { company = it }
                EditField(R.string.m3_edit_profile_location, location) { location = it }
                EditField(R.string.m3_edit_profile_blog, blog) { blog = it }
                EditField(R.string.m3_edit_profile_email, email) { email = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(ProfileEdit(name, bio, company, location, blog, email))
                },
                enabled = !saving,
            ) { Text(stringResource(R.string.m3_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(stringResource(R.string.m3_cancel))
            }
        },
    )
}

@Composable
private fun EditField(
    @StringRes label: Int,
    value: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth(),
    )
}
