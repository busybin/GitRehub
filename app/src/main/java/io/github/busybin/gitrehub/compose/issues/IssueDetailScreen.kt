package io.github.busybin.gitrehub.compose.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.CompactButton
import io.github.busybin.gitrehub.compose.common.ConfirmDialog
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.LinkCopyDialog
import io.github.busybin.gitrehub.compose.common.MarkdownText
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.compose.common.repoBlobBaseUrl
import io.github.busybin.gitrehub.compose.common.repoRawBaseUrl
import io.github.busybin.gitrehub.core.model.Comment
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.model.PullRequest
import io.github.busybin.gitrehub.core.model.Reactions
import io.github.busybin.gitrehub.core.model.Commit
import io.github.busybin.gitrehub.helper.BrowserLauncher
import kotlinx.coroutines.delay

/**
 * Issue / PR detail with write support: comment (incl. edit/delete), reactions, title/body,
 * labels, assignees, milestone, lock, close/reopen and merge PR.
 *
 * Writes show a button block, a thin progress bar and a one-shot banner, then reload the screen.
 */
@Composable
fun IssueDetailScreen(
    owner: String,
    repo: String,
    number: Int,
    /** When set, a PR commit can be tapped to open its diff. */
    onOpenCommit: ((String, String, String) -> Unit)? = null,
    /** When set, the PR's "files changed" line opens the review screen. */
    onOpenFiles: ((String, String, Int) -> Unit)? = null,
) {
    val vm: IssueDetailViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val myLogin by vm.myLogin.collectAsStateWithLifecycle()
    val myReactions by vm.myReactions.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCommentSheet by rememberSaveable { mutableStateOf(false) }
    var showEditIssue by rememberSaveable { mutableStateOf(false) }
    var showManage by rememberSaveable { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<Comment?>(null) }
    var deletingComment by remember { mutableStateOf<Comment?>(null) }
    var confirmToggle by rememberSaveable { mutableStateOf(false) }
    var showMergeDialog by rememberSaveable { mutableStateOf(false) }
    var linkToCopy by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(owner, repo, number) { vm.load(owner, repo, number) }

    // Fetch manage candidates only when the drawer opens.
    LaunchedEffect(showManage) {
        if (showManage) vm.loadOptions(owner, repo)
    }

    // Let the banner fade away instead of requiring a tap.
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            vm.consumeMessage()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        message?.let { WriteBanner(it, onDismiss = { vm.consumeMessage() }) }

        AsyncContent(
            state = state,
            onRetry = { vm.load(owner, repo, number, force = true) },
        ) { data ->
            val issue = data.issue
            val pr = data.pullRequest
            // Permissions come from `GET /repos/{o}/{r}`: on someone else's repository the write
            // buttons stay visible but disabled instead of failing with a 403 after the tap.
            val canWrite = data.permissions?.canWrite == true
            val canTriage = data.permissions?.canTriage == true
            val isAuthor = myLogin != null && issue.user?.login.equals(myLogin, ignoreCase = true)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = issue.title.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item { StateRow(issue, data.pullRequest, number) }

                item {
                    WriteActions(
                        busy = busy,
                        isPull = pr != null,
                        closed = issue.isClosed,
                        merged = pr?.merged == true,
                        canComment = !issue.locked || canWrite,
                        canEdit = canTriage || isAuthor,
                        canManage = canTriage,
                        canClose = canTriage || isAuthor,
                        canMerge = canWrite,
                        onComment = { showCommentSheet = true },
                        onEdit = { showEditIssue = true },
                        onManage = { showManage = true },
                        onToggleState = { confirmToggle = true },
                        onMerge = { showMergeDialog = true },
                    )
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            CommentHeader(
                                login = issue.user?.login,
                                avatar = issue.user?.avatarUrl,
                                time = issue.createdAt,
                                association = null,
                            )
                            Spacer(Modifier.height(10.dp))
                            if (issue.body.isNullOrBlank()) {
                                Text(
                                    stringResource(R.string.m3_issue_no_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                MarkdownText(
                                    markdown = issue.body,
                                    baseUrl = repoRawBaseUrl(owner, repo),
                                    linkBaseUrl = repoBlobBaseUrl(owner, repo),
                                    repo = "$owner/$repo",
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            ReactionBar(
                                reactions = issue.reactions,
                                prefix = "issue",
                                mine = myReactions,
                                enabled = !busy,
                                onToggle = { kind ->
                                    vm.toggleReaction(
                                        owner, repo,
                                        ReactionTarget.IssueLevel(number), kind,
                                    )
                                },
                            )
                        }
                    }
                }

                if (data.comments.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text(
                                "  " + stringResource(R.string.m3_issue_comments, data.comments.size) + "  ",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(Modifier.weight(1f))
                        }
                    }
                    items(data.comments, key = { it.id }) { comment ->
                        CommentCard(
                            comment = comment,
                            mine = myLogin != null &&
                                comment.user?.login.equals(myLogin, ignoreCase = true),
                            imageBaseUrl = repoRawBaseUrl(owner, repo),
                            linkBaseUrl = repoBlobBaseUrl(owner, repo),
                            repo = "$owner/$repo",
                            reactionsMine = myReactions,
                            busy = busy,
                            onEdit = { editingComment = comment },
                            onDelete = { deletingComment = comment },
                            onReact = { kind ->
                                vm.toggleReaction(
                                    owner, repo,
                                    ReactionTarget.CommentLevel(comment.id), kind,
                                )
                            },
                        )
                    }
                }

                // PR commits and diff stats.
                if (pr != null) {
                    item {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.m3_pr_commits, data.commits.size),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.weight(1f))
                                if (pr.additions > 0) {
                                    Text(
                                        text = stringResource(R.string.m3_pr_additions, pr.additions),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF3FB950),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                if (pr.deletions > 0) {
                                    Text(
                                        text = stringResource(R.string.m3_pr_deletions, pr.deletions),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.m3_pr_files_changed, pr.changedFiles),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (onOpenFiles != null) {
                                    TextButton(onClick = { onOpenFiles.invoke(owner, repo, number) }) {
                                        Text(stringResource(R.string.m3_pr_review_title))
                                    }
                                }
                            }
                        }
                    }
                    if (data.commits.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.m3_pr_no_commits),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(data.commits, key = { it.sha }) { commit ->
                            CommitRow(
                                commit = commit,
                                onClick = { onOpenCommit?.invoke(owner, repo, commit.sha) },
                            )
                        }
                    }
                }

                item {
                    TextButton(
                        onClick = {
                            val url = issue.htmlUrl
                                ?: "https://github.com/$owner/$repo/issues/$number"
                            // Use BrowserLauncher: the app declares a github.com filter, so a raw VIEW intent loops back.
                            if (!BrowserLauncher.open(context, url)) linkToCopy = url
                        }
                    ) {
                        Icon(
                            Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.m3_open_in_browser))
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    if (showCommentSheet) {
        EditSheet(
            heading = stringResource(R.string.m3_write_comment),
            showTitle = false,
            initialTitle = "",
            initialBody = "",
            submitLabel = stringResource(R.string.m3_write_comment_send),
            onDismiss = { showCommentSheet = false },
            onSubmit = { _, body ->
                showCommentSheet = false
                vm.postComment(owner, repo, number, body)
            },
        )
    }

    editingComment?.let { comment ->
        EditSheet(
            heading = stringResource(R.string.m3_write_edit_comment),
            showTitle = false,
            initialTitle = "",
            initialBody = comment.body.orEmpty(),
            submitLabel = stringResource(R.string.m3_write_save),
            onDismiss = { editingComment = null },
            onSubmit = { _, body ->
                editingComment = null
                vm.editComment(owner, repo, number, comment.id, body)
            },
        )
    }

    deletingComment?.let { comment ->
        ConfirmDialog(
            title = stringResource(R.string.m3_write_delete_comment_title),
            message = stringResource(R.string.m3_write_delete_comment_message),
            confirmLabel = stringResource(R.string.m3_write_delete),
            destructive = true,
            onConfirm = {
                deletingComment = null
                vm.deleteComment(owner, repo, number, comment.id)
            },
            onDismiss = { deletingComment = null },
        )
    }

    if (showEditIssue) {
        val data = (state as? LoadState.Ready)?.value
        EditSheet(
            heading = stringResource(
                if (data?.pullRequest != null) R.string.m3_write_edit_pull else R.string.m3_write_edit_issue
            ),
            showTitle = true,
            initialTitle = data?.issue?.title.orEmpty(),
            initialBody = data?.issue?.body.orEmpty(),
            submitLabel = stringResource(R.string.m3_write_save),
            onDismiss = { showEditIssue = false },
            onSubmit = { title, body ->
                showEditIssue = false
                vm.editIssue(owner, repo, number, title, body)
            },
        )
    }

    if (showManage) {
        val data = (state as? LoadState.Ready)?.value
        if (data != null) {
            ManageSheet(
                issue = data.issue,
                options = options,
                busy = busy,
                onRetryOptions = { vm.loadOptions(owner, repo, force = true) },
                onDismiss = { showManage = false },
                onSave = { labels, assignees, milestone, milestoneChanged, locked ->
                    showManage = false
                    vm.applyManagement(
                        owner, repo, number, data.issue,
                        labels, assignees, milestone, milestoneChanged, locked,
                    )
                },
            )
        }
    }

    if (confirmToggle) {
        val data = (state as? LoadState.Ready)?.value
        val closing = data?.issue?.isClosed != true
        ConfirmDialog(
            title = stringResource(
                if (closing) R.string.m3_write_close_title else R.string.m3_write_reopen_title
            ),
            message = stringResource(
                if (closing) R.string.m3_write_close_message else R.string.m3_write_reopen_message
            ),
            confirmLabel = stringResource(
                if (closing) R.string.m3_write_close else R.string.m3_write_reopen
            ),
            destructive = closing,
            onConfirm = {
                confirmToggle = false
                if (data != null) {
                    vm.toggleState(owner, repo, number, data.issue.isClosed)
                }
            },
            onDismiss = { confirmToggle = false },
        )
    }

    if (showMergeDialog) {
        MergeDialog(
            number = number,
            onDismiss = { showMergeDialog = false },
            onConfirm = { method, commitMessage ->
                showMergeDialog = false
                vm.mergePullRequest(owner, repo, number, method, commitMessage)
            },
        )
    }

    linkToCopy?.let { url -> LinkCopyDialog(url = url) { linkToCopy = null } }
}

/**
 * Write action row: comment / edit / manage / close-reopen / merge.
 * Merge only shows for an open, unmerged PR.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WriteActions(
    busy: Boolean,
    isPull: Boolean,
    closed: Boolean,
    merged: Boolean,
    canComment: Boolean,
    canEdit: Boolean,
    canManage: Boolean,
    canClose: Boolean,
    canMerge: Boolean,
    onComment: () -> Unit,
    onEdit: () -> Unit,
    onManage: () -> Unit,
    onToggleState: () -> Unit,
    onMerge: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactButton(
            text = stringResource(R.string.m3_write_comment),
            onClick = onComment,
            enabled = !busy && canComment,
        )
        CompactButton(
            text = stringResource(R.string.m3_write_edit),
            onClick = onEdit,
            enabled = !busy && canEdit,
        )
        CompactButton(
            text = stringResource(R.string.m3_write_manage),
            onClick = onManage,
            enabled = !busy && canManage,
        )
        // A merged PR cannot be closed or reopened, so hide the toggle.
        if (!merged) {
            CompactButton(
                text = stringResource(
                    if (closed) R.string.m3_write_reopen else R.string.m3_write_close
                ),
                onClick = onToggleState,
                enabled = !busy && canClose,
            )
        }
        if (isPull && !merged && !closed) {
            CompactButton(
                text = stringResource(R.string.m3_write_merge),
                onClick = onMerge,
                enabled = !busy && canMerge,
            )
        }
    }
}

/** Reaction bar: shows only reactions already present (or mine); the other eight are in the "+" menu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionBar(
    reactions: Reactions?,
    prefix: String,
    mine: Set<String>,
    enabled: Boolean,
    onToggle: (ReactionKind) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ReactionKind.entries.forEach { kind ->
            val count = reactionCount(reactions, kind)
            val mineOne = mine.contains("$prefix:${kind.api}")
            if (count > 0 || mineOne) {
                ReactionChip(kind, count, mineOne, enabled) { onToggle(kind) }
            }
        }
        Box {
            IconButton(onClick = { expanded = true }, enabled = enabled) {
                Icon(
                    Icons.Outlined.AddReaction,
                    contentDescription = stringResource(R.string.m3_reaction_add),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ReactionKind.entries.forEach { kind ->
                    DropdownMenuItem(
                        text = { Text("${kind.emoji}  ${stringResource(kind.labelRes)}") },
                        onClick = {
                            expanded = false
                            onToggle(kind)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionChip(
    kind: ReactionKind,
    count: Int,
    mine: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (mine) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .then(if (enabled) Modifier.clickable { onToggle() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(kind.emoji, style = MaterialTheme.typography.labelMedium)
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (mine) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun reactionCount(reactions: Reactions?, kind: ReactionKind): Int = when (kind) {
    ReactionKind.PLUS_ONE -> reactions?.plusOne
    ReactionKind.MINUS_ONE -> reactions?.minusOne
    ReactionKind.LAUGH -> reactions?.laugh
    ReactionKind.HOORAY -> reactions?.hooray
    ReactionKind.CONFUSED -> reactions?.confused
    ReactionKind.HEART -> reactions?.heart
    ReactionKind.ROCKET -> reactions?.rocket
    ReactionKind.EYES -> reactions?.eyes
} ?: 0

/** Shared input sheet for writing/editing a comment or editing an issue; `showTitle` adds a title field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    heading: String,
    showTitle: Boolean,
    initialTitle: String,
    initialBody: String,
    submitLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            if (showTitle) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.m3_write_title_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text(stringResource(R.string.m3_write_comment_hint)) },
                minLines = 5,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSubmit(title, body) },
                    enabled = body.isNotBlank() && (!showTitle || title.isNotBlank()),
                ) {
                    Text(submitLabel)
                }
            }
        }
    }
}

/** Manage drawer: labels, assignees, milestone and locked conversation; the ViewModel diffs the selection. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ManageSheet(
    issue: Issue,
    options: ManageOptions,
    busy: Boolean,
    onRetryOptions: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (List<String>, List<String>, Int?, Boolean, Boolean) -> Unit,
) {
    val allLabels = remember(options.labels) { options.labels.mapNotNull { it.name } }
    val allAssignees = remember(options.assignees) { options.assignees.mapNotNull { it.login } }
    val currentMilestone = issue.milestone?.number
    val currentLabels = remember(issue.id) { issue.labels.mapNotNull { it.name } }
    val currentAssignees = remember(issue.id) {
        issue.assignees.mapNotNull { it.login }
            .ifEmpty { listOfNotNull(issue.assignee?.login) }
    }

    var selectedLabels by remember(issue.id, allLabels) { mutableStateOf(currentLabels.toSet()) }
    var selectedAssignees by remember(issue.id, allAssignees) {
        mutableStateOf(currentAssignees.toSet())
    }
    var milestone by remember(issue.id) { mutableStateOf(currentMilestone) }
    var locked by remember(issue.id) { mutableStateOf(issue.locked) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.m3_write_manage),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.m3_write_manage_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            if (options.loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            if (options.failed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.m3_write_manage_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetryOptions) { Text(stringResource(R.string.m3_retry)) }
                }
            }

            // Labels
            Text(stringResource(R.string.m3_write_labels), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (allLabels.isEmpty()) {
                MetaText(stringResource(R.string.m3_write_no_labels))
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    allLabels.forEach { name ->
                        FilterChip(
                            selected = selectedLabels.contains(name),
                            onClick = {
                                selectedLabels = if (selectedLabels.contains(name)) {
                                    selectedLabels - name
                                } else {
                                    selectedLabels + name
                                }
                            },
                            label = { Text(name) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Assignees
            Text(stringResource(R.string.m3_write_assignees), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (allAssignees.isEmpty()) {
                MetaText(stringResource(R.string.m3_write_no_assignees))
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    allAssignees.forEach { login ->
                        FilterChip(
                            selected = selectedAssignees.contains(login),
                            onClick = {
                                selectedAssignees = if (selectedAssignees.contains(login)) {
                                    selectedAssignees - login
                                } else {
                                    selectedAssignees + login
                                }
                            },
                            label = { Text(login) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Milestone
            Text(stringResource(R.string.m3_write_milestone), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                FilterChip(
                    selected = milestone == null,
                    onClick = { milestone = null },
                    label = { Text(stringResource(R.string.m3_write_no_milestone)) },
                )
                options.milestones.forEach { item ->
                    FilterChip(
                        selected = milestone == item.number,
                        onClick = { milestone = item.number },
                        label = { Text(item.title.orEmpty()) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Lock
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.m3_write_lock),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    MetaText(stringResource(R.string.m3_write_lock_desc))
                }
                Switch(checked = locked, onCheckedChange = { locked = it })
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            selectedLabels.toList(),
                            selectedAssignees.toList(),
                            milestone,
                            milestone != currentMilestone,
                            locked,
                        )
                    },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.m3_write_save))
                }
            }
        }
    }
}

/** Merge confirmation: pick a merge method and an optional commit message (merge is irreversible). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MergeDialog(
    number: Int,
    onDismiss: () -> Unit,
    onConfirm: (MergeMethod, String?) -> Unit,
) {
    var method by rememberSaveable { mutableStateOf(MergeMethod.MERGE) }
    var commitMessage by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m3_write_merge_title, number)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.m3_write_merge_method),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MergeMethod.entries.forEach { option ->
                        FilterChip(
                            selected = method == option,
                            onClick = { method = option },
                            label = { Text(stringResource(option.labelRes)) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.m3_write_merge_message_hint)) },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(method, commitMessage) }) {
                Text(stringResource(R.string.m3_write_merge))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_cancel)) }
        },
    )
}

/** One-shot success/failure banner (green on success, red on failure). */
@Composable
private fun WriteBanner(message: WriteMessage, onDismiss: () -> Unit) {
    val container = if (message.error) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val content = if (message.error) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSecondaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (message.error) Icons.Outlined.ErrorOutline else Icons.Outlined.TaskAlt,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (message.error) {
                    stringResource(R.string.m3_write_failed, message.text.render())
                } else {
                    message.text.render()
                },
                style = MaterialTheme.typography.bodyMedium,
                color = content,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_done)) }
        }
    }
}

@Composable
private fun StateRow(issue: Issue, pr: PullRequest?, number: Int) {
    val closed = issue.isClosed
    val merged = pr?.merged == true
    val stateLabel = stringResource(
        when {
            merged -> R.string.m3_issue_merged
            closed -> R.string.m3_issue_closed
            else -> R.string.m3_issue_open
        }
    )
    val stateColor = when {
        merged -> Color(0xFF8250DF)
        closed -> Color(0xFFCF222E)
        else -> Color(0xFF1A7F37)
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(stateColor)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when {
                        merged || (pr != null && !closed) -> Icons.Outlined.CallMerge
                        closed -> Icons.Outlined.TaskAlt
                        else -> Icons.Outlined.ErrorOutline
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
            if (pr != null) Pill("PR #$number") else Pill("Issue #$number")
            MetaText(relativeTime(issue.createdAt))
        }

        if (pr != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("+${pr.additions}", container = Color(0xFFE6FFEC), content = Color(0xFF1A7F37))
                Pill("-${pr.deletions}", container = Color(0xFFFFEBE9), content = Color(0xFFCF222E))
                Pill(stringResource(R.string.m3_issue_files, pr.changedFiles))
            }
        }

        val labels = issue.labels
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEach { label -> Pill(label.name.orEmpty()) }
            }
        }
        if (issue.locked) {
            Spacer(Modifier.height(8.dp))
            Pill(stringResource(R.string.m3_issue_locked))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentCard(
    comment: Comment,
    mine: Boolean,
    imageBaseUrl: String? = null,
    linkBaseUrl: String? = null,
    repo: String? = null,
    reactionsMine: Set<String> = emptySet(),
    busy: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReact: (ReactionKind) -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            CommentHeader(
                login = comment.user?.login,
                avatar = comment.user?.avatarUrl,
                time = comment.createdAt,
                association = comment.authorAssociation,
                actions = if (mine) {
                    { CommentMenu(onEdit = onEdit, onDelete = onDelete) }
                } else {
                    null
                },
            )
            Spacer(Modifier.height(10.dp))
            if (comment.body.isNullOrBlank()) {
                Text(
                    stringResource(R.string.m3_issue_empty_comment),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MarkdownText(
                    markdown = comment.body,
                    baseUrl = imageBaseUrl,
                    linkBaseUrl = linkBaseUrl,
                    repo = repo,
                )
            }
            Spacer(Modifier.height(10.dp))
            ReactionBar(
                reactions = comment.reactions,
                prefix = "comment:${comment.id}",
                mine = reactionsMine,
                enabled = !busy,
                onToggle = onReact,
            )
        }
    }
}

/** Overflow menu on my own comments: edit / delete (delete is red and goes through a confirm dialog). */
@Composable
private fun CommentMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.m3_menu),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.m3_write_edit)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.m3_write_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun CommentHeader(
    login: String?,
    avatar: String?,
    time: java.time.Instant?,
    association: String?,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(avatar, size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = login ?: "unknown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (association != null && association != "NONE") {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.m3_issue_author),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            MetaText(relativeTime(time))
        }
        actions?.invoke()
    }
}

/** One PR commit: short sha, first line, author and time; tapping opens the commit diff. */
@Composable
private fun CommitRow(commit: Commit, onClick: (() -> Unit)? = null) {
    val message = commit.messageTitle
    val author = commit.authorName
    val date = commit.date
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp),
        modifier = if (onClick != null) Modifier.fillMaxWidth().clickable { onClick() } else Modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = commit.shortSha,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (author != null) {
                    MetaText(buildString {
                        append(author)
                        if (date != null) {
                            append(" · ")
                            append(relativeTime(date))
                        }
                    })
                }
            }
            if (onClick != null) {
                Icon(
                    Icons.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
