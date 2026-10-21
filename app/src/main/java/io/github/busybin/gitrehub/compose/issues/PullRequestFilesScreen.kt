package io.github.busybin.gitrehub.compose.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.core.model.CheckRun
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.EmptyView
import io.github.busybin.gitrehub.compose.common.LoadState
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.model.CommitFile
import io.github.busybin.gitrehub.core.model.ReviewComment
import io.github.busybin.gitrehub.core.model.Review
import kotlinx.coroutines.delay

/** Where an inline comment would be attached: the file, the side of the diff and the file line. */
private data class CommentTarget(val path: String, val line: DiffLine)

/**
 * "Files changed" of one pull request: the whole diff in one screen, the reviews so far, the checks
 * of the head commit, and the review itself (inline comments, then comment / approve / request
 * changes in one submission).
 */
@Composable
fun PullRequestFilesScreen(owner: String, repo: String, number: Int) {
    val vm: PullRequestFilesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    // Server-side reviews need write access; read-only visitors get a view-only diff instead.
    val canReview = ((state as? LoadState.Ready)?.value)?.permissions?.canWrite == true

    var expanded by remember { mutableStateOf(emptyList<String>()) }
    var viewed by remember { mutableStateOf(emptyList<String>()) }
    var commentTarget by remember { mutableStateOf<CommentTarget?>(null) }
    var showReview by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(owner, repo, number) { vm.load(owner, repo, number) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            vm.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { FilesBanner(it, onDismiss = { vm.consumeMessage() }) }

            AsyncContent(
                state = state,
                onRetry = { vm.load(owner, repo, number, force = true) },
            ) { data ->
                val commentsByAnchor = remember(data.comments) { groupComments(data.comments) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { PullSummary(data) }
                    if (data.checks.isNotEmpty()) {
                        item { ChecksRow(data.checks) }
                    }
                    item {
                        ReviewStatus(
                            reviews = data.reviews,
                            draftCount = draft.size,
                            canReview = canReview,
                            onReview = { showReview = true },
                        )
                    }
                    if (data.truncated) {
                        item {
                            Text(
                                text = stringResource(R.string.m3_pr_files_truncated, data.files.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (data.files.isEmpty()) {
                        item { EmptyView(stringResource(R.string.m3_pr_files_empty)) }
                    } else {
                        items(data.files, key = { it.filename ?: it.sha ?: "file" }) { file ->
                            val path = file.filename.orEmpty()
                            FileDiffCard(
                                file = file,
                                expanded = expanded.contains(path),
                                viewed = viewed.contains(path),
                                canComment = canReview,
                                comments = commentsByAnchor,
                                draft = draft.withIndex().filter { it.value.path == path },
                                onToggleViewed = {
                                    viewed = if (viewed.contains(path)) viewed - path else viewed + path
                                },
                                onToggleExpand = {
                                    expanded = if (expanded.contains(path)) {
                                        expanded - path
                                    } else {
                                        expanded + path
                                    }
                                },
                                onComment = { line -> commentTarget = CommentTarget(path, line) },
                                onRemoveDraft = { index -> vm.removeDraft(index) },
                            )
                        }
                    }
                    if (data.reviews.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.m3_pr_reviews),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        items(data.reviews, key = { it.id }) { review -> ReviewRow(review) }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }

        if (state is LoadState.Ready && canReview) {
            ExtendedFloatingActionButton(
                onClick = { showReview = true },
                icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                text = { Text(stringResource(R.string.m3_pr_review_title)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp),
            )
        }
    }

    commentTarget?.let { target ->
        InlineCommentDialog(
            path = target.path,
            line = target.line,
            onDismiss = { commentTarget = null },
            onSubmit = { body ->
                commentTarget = null
                target.line.commentLine?.let { line ->
                    target.line.commentSide?.let { side ->
                        vm.addDraft(target.path, line, side, body)
                    }
                }
            },
        )
    }

    if (showReview) {
        val data = (state as? LoadState.Ready)?.value
        if (data != null) {
            ReviewDialog(
                draftCount = draft.size,
                canWrite = canReview,
                ownPullRequest = !data.myLogin.isNullOrBlank() &&
                    data.myLogin.equals(data.pullRequest.user?.login, ignoreCase = true),
                onDismiss = { showReview = false },
                onSubmit = { event, body ->
                    showReview = false
                    vm.submitReview(owner, repo, number, event, body)
                },
            )
        }
    }
}

/** `"SIDE:line"` → the comments anchored on that exact line, oldest first. */
private fun groupComments(comments: List<ReviewComment>): Map<String, List<ReviewComment>> =
    comments
        .filter { it.path != null && it.line != null }
        .sortedBy { it.createdAt }
        .groupBy { anchor(it.side ?: "RIGHT", it.line!!) }

private fun anchor(side: String, line: Int) = "$side:$line"

@Composable
private fun PullSummary(data: PullRequestFilesData) {
    val pr = data.pullRequest
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = pr.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${pr.base?.ref.orEmpty()} ← ${pr.head?.ref.orEmpty()}",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pr.head?.sha?.take(7).orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("+${pr.additions}", container = Color(0x223FB950), content = Color(0xFF7EE787))
                Pill("−${pr.deletions}", container = Color(0x22F85149), content = Color(0xFFFFA198))
                Pill(stringResource(R.string.m3_pr_files_changed, data.files.size))
            }
        }
    }
}

/** Checks of the head commit, as counted pills plus the names of the failing ones. */
@Composable
private fun ChecksRow(checks: List<CheckRun>) {
    val passed = checks.count { it.isDone && it.isOk }
    val failed = checks.count { it.isDone && !it.isOk }
    val running = checks.count { !it.isDone }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.m3_pr_checks),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (passed > 0) {
                    Pill(
                        stringResource(R.string.m3_pr_checks_passed, passed),
                        container = Color(0x223FB950),
                        content = Color(0xFF7EE787),
                    )
                }
                if (failed > 0) {
                    Pill(
                        stringResource(R.string.m3_pr_checks_failed, failed),
                        container = Color(0x22F85149),
                        content = Color(0xFFFFA198),
                    )
                }
                if (running > 0) {
                    Pill(stringResource(R.string.m3_pr_checks_running, running))
                }
            }
            checks.filter { it.isDone && !it.isOk }.take(6).forEach { check ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = check.name.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ReviewStatus(
    reviews: List<Review>,
    draftCount: Int,
    canReview: Boolean,
    onReview: () -> Unit,
) {
    val approved = reviews.count { it.state == "APPROVED" }
    val changes = reviews.count { it.state == "CHANGES_REQUESTED" }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (approved > 0) {
                    Pill(
                        stringResource(R.string.m3_pr_review_approved_count, approved),
                        container = Color(0x223FB950),
                        content = Color(0xFF7EE787),
                    )
                }
                if (changes > 0) {
                    Pill(
                        stringResource(R.string.m3_pr_review_changes_count, changes),
                        container = Color(0x22F85149),
                        content = Color(0xFFFFA198),
                    )
                }
                if (approved == 0 && changes == 0) {
                    Pill(stringResource(R.string.m3_pr_review_none))
                }
            }
            if (draftCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.m3_pr_review_pending, draftCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            if (canReview) {
                TextButton(onClick = onReview, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.m3_pr_review_title))
                }
            } else {
                Text(
                    text = stringResource(R.string.m3_pr_review_no_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start),
                )
            }
        }
    }
}

@Composable
private fun FileDiffCard(
    file: CommitFile,
    expanded: Boolean,
    viewed: Boolean,
    canComment: Boolean,
    comments: Map<String, List<ReviewComment>>,
    draft: List<IndexedValue<DraftComment>>,
    onToggleViewed: () -> Unit,
    onToggleExpand: () -> Unit,
    onComment: (DiffLine) -> Unit,
    onRemoveDraft: (Int) -> Unit,
) {
    val path = file.filename.orEmpty()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpand() },
                )
                if (file.status != null && file.status != "modified") {
                    Spacer(Modifier.width(6.dp))
                    Pill(changeWord(file.status!!))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "+${file.additions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7EE787),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "−${file.deletions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFA198),
                )
                Spacer(Modifier.weight(1f))
                Checkbox(checked = viewed, onCheckedChange = { onToggleViewed() })
                Text(
                    text = stringResource(R.string.m3_pr_viewed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!expanded) {
                TextButton(onClick = onToggleExpand) {
                    Text(stringResource(R.string.m3_pr_show_diff))
                }
                return@Column
            }
            val lines = remember(file.patch) { parsePatch(file.patch) }
            if (lines.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.m3_commit_patch_too_big),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                lines.forEach { line ->
                    DiffRow(
                        line = line,
                        canComment = canComment,
                        commentsFor = comments,
                        draft = draft,
                        onComment = onComment,
                        onRemoveDraft = onRemoveDraft,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiffRow(
    line: DiffLine,
    canComment: Boolean,
    commentsFor: Map<String, List<ReviewComment>>,
    draft: List<IndexedValue<DraftComment>>,
    onComment: (DiffLine) -> Unit,
    onRemoveDraft: (Int) -> Unit,
) {
    val commentable = canComment && line.commentLine != null
    val background = when (line.kind) {
        DiffLine.Kind.ADDED -> Color(0x223FB950)
        DiffLine.Kind.REMOVED -> Color(0x22F85149)
        else -> null
    }
    val textColor = when (line.kind) {
        DiffLine.Kind.ADDED -> Color(0xFF7EE787)
        DiffLine.Kind.REMOVED -> Color(0xFFFFA198)
        DiffLine.Kind.HUNK -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (background != null) Modifier.background(background) else Modifier)
            .then(if (commentable) Modifier.clickable { onComment(line) } else Modifier)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = line.oldLine?.toString().orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp),
        )
        Text(
            text = line.newLine?.toString().orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp),
        )
        Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = if (commentable) MaterialTheme.colorScheme.primary else Color.Transparent,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (line.text.isEmpty()) " " else line.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
    }
    val anchorKey = line.commentLine?.let { anchor(line.commentSide ?: "RIGHT", it) }
    commentsFor[anchorKey].orEmpty().forEach { comment -> ExistingComment(comment) }
    draft.filter { d ->
        anchorKey != null && anchor(d.value.side, d.value.line) == anchorKey
    }.forEach { entry ->
        DraftCommentRow(entry.value.body, onDelete = { onRemoveDraft(entry.index) })
    }
}

@Composable
private fun ExistingComment(comment: ReviewComment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, bottom = 4.dp),
    ) {
        Avatar(comment.user?.avatarUrl, size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user?.login.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(6.dp))
                MetaText(relativeTime(comment.createdAt))
            }
            Text(
                text = comment.body.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DraftCommentRow(body: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.m3_cancel),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ReviewRow(review: Review) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Avatar(review.user?.avatarUrl, size = 26.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = review.user?.login.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Pill(reviewStateWord(review.state))
            }
            MetaText(relativeTime(review.submittedAt))
            val body = review.body
            if (!body.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun reviewStateWord(state: String?): String = when (state) {
    "APPROVED" -> stringResource(R.string.m3_pr_review_state_approved)
    "CHANGES_REQUESTED" -> stringResource(R.string.m3_pr_review_state_changes)
    "DISMISSED" -> stringResource(R.string.m3_pr_review_state_dismissed)
    "PENDING" -> stringResource(R.string.m3_pr_review_state_pending)
    else -> stringResource(R.string.m3_pr_review_state_commented)
}

@Composable
private fun changeWord(status: String) = stringResource(
    when (status) {
        "added" -> R.string.m3_commit_status_added
        "removed" -> R.string.m3_commit_status_removed
        "renamed" -> R.string.m3_commit_status_renamed
        "copied" -> R.string.m3_commit_status_copied
        "changed" -> R.string.m3_commit_status_changed
        else -> R.string.m3_commit_status_modified
    }
)

@Composable
private fun InlineCommentDialog(
    path: String,
    line: DiffLine,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    R.string.m3_pr_line_comment_title,
                    path.substringAfterLast('/'),
                    line.commentLine ?: 0,
                )
            )
        },
        text = {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.m3_pr_comment_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(body) }, enabled = body.isNotBlank()) {
                Text(stringResource(R.string.m3_pr_comment_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_cancel)) }
        },
    )
}

@Composable
private fun ReviewDialog(
    draftCount: Int,
    canWrite: Boolean,
    ownPullRequest: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m3_pr_review_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.m3_pr_review_body_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draftCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.m3_pr_review_pending, draftCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (ownPullRequest) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.m3_pr_review_own_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Stacked actions: three labels do not fit side by side on a phone dialog.
                TextButton(
                    onClick = { onSubmit("COMMENT", body) },
                    enabled = canWrite,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.m3_pr_review_comment))
                }
                if (canWrite && !ownPullRequest) {
                    TextButton(
                        onClick = { onSubmit("APPROVE", body) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.m3_pr_review_approve))
                    }
                    TextButton(
                        onClick = { onSubmit("REQUEST_CHANGES", body) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.m3_pr_review_request_changes),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.m3_cancel))
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun FilesBanner(message: FilesMessage, onDismiss: () -> Unit) {
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
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message.text.render(),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            }
        }
    }
}
