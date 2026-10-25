package io.github.busybin.gitrehub.compose.repos

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.AsyncContent
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.ConfirmDialog
import io.github.busybin.gitrehub.compose.common.HtmlText
import io.github.busybin.gitrehub.compose.common.LinkCopyDialog
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.compactNumber
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.compose.common.rememberDownloader
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.compose.common.ToggleActionButton
import io.github.busybin.gitrehub.compose.common.repoBlobBaseUrl
import io.github.busybin.gitrehub.compose.common.repoRawBaseUrl
import io.github.busybin.gitrehub.helper.BrowserLauncher
import io.github.busybin.gitrehub.provider.colors.ColorsProvider
import kotlinx.coroutines.launch

/**
 * Repository detail — the "do it all in the app" page.
 *
 * Counts are tappable (stars -> stargazers, forks -> fork list), issues and PRs are split with their
 * open counts, branches/tags/contributors carry counts, and there is a language bar, contributor
 * avatars and an activity entry. Features a repo does not have are greyed out; star, fork and watch
 * ask for confirmation first, and an active button (blue) toggles the action back off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    name: String,
    onOpenIssues: (String, String) -> Unit = { _, _ -> },
    onOpenPulls: (String, String) -> Unit = { _, _ -> },
    onOpenReleases: (String, String) -> Unit = { _, _ -> },
    onOpenCode: (String, String) -> Unit = { _, _ -> },
    onOpenBranches: (String, String) -> Unit = { _, _ -> },
    onOpenTags: (String, String) -> Unit = { _, _ -> },
    onOpenContributors: (String, String) -> Unit = { _, _ -> },
    onOpenStargazers: (String, String) -> Unit = { _, _ -> },
    onOpenWatchers: (String, String) -> Unit = { _, _ -> },
    onOpenForks: (String, String) -> Unit = { _, _ -> },
    onOpenDiscussions: (String, String) -> Unit = { _, _ -> },
    onOpenActions: (String, String) -> Unit = { _, _ -> },
    onOpenActivity: (String, String) -> Unit = { _, _ -> },
    onOpenLabels: (String, String) -> Unit = { _, _ -> },
    onOpenMilestones: (String, String) -> Unit = { _, _ -> },
    onOpenUser: (String) -> Unit = {},
    onOpenRepo: (String, String) -> Unit = { _, _ -> },
) {
    val vm: RepoDetailViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val starBusy by vm.starBusy.collectAsStateWithLifecycle()
    val forkBusy by vm.forkBusy.collectAsStateWithLifecycle()
    val forkOwned by vm.forkOwned.collectAsStateWithLifecycle()
    val forked by vm.forked.collectAsStateWithLifecycle()
    val forkFullName by vm.forkFullName.collectAsStateWithLifecycle()
    val watchBusy by vm.watchBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val download = rememberDownloader()
    val message by vm.message.collectAsStateWithLifecycle()
    val messageText = message?.render()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            Toast.makeText(context, messageText, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }
    var linkToCopy by remember { mutableStateOf<String?>(null) }
    var confirmStar by remember { mutableStateOf(false) }
    var confirmWatch by remember { mutableStateOf(false) }
    var showForkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(owner, name) { vm.load(owner, name) }

    AsyncContent(
        state = state,
        onRetry = { vm.load(owner, name, force = true) },
    ) { detail ->
        val repo = detail.repo
        val scrollState = rememberScrollState()
        val density = LocalDensity.current
        val showBackToTop by remember {
            derivedStateOf { scrollState.value > with(density) { 900.dp.toPx() } }
        }

        if (confirmStar) {
            ConfirmDialog(
                title = stringResource(
                    if (detail.starring) R.string.m3_unstar_confirm_title else R.string.m3_star_confirm_title,
                    repo.fullName.ifBlank { "$owner/$name" },
                ),
                message = stringResource(
                    if (detail.starring) R.string.m3_unstar_confirm_message else R.string.m3_star_confirm_message
                ),
                confirmLabel = stringResource(if (detail.starring) R.string.m3_unstar else R.string.m3_star),
                onConfirm = {
                    confirmStar = false
                    vm.toggleStar()
                },
                onDismiss = { confirmStar = false },
            )
        }
        if (showForkDialog) {
            ForkDialog(
                repoName = repo.name.ifBlank { name },
                busy = forkBusy,
                onConfirm = { forkName, defaultBranchOnly ->
                    showForkDialog = false
                    vm.forkRepo(forkName, defaultBranchOnly)
                },
                onDismiss = { showForkDialog = false },
            )
        }
        if (confirmWatch) {
            ConfirmDialog(
                title = stringResource(
                    if (detail.watching) R.string.m3_unwatch_confirm_title else R.string.m3_watch_confirm_title,
                    repo.fullName.ifBlank { "$owner/$name" },
                ),
                message = stringResource(
                    if (detail.watching) R.string.m3_unwatch_confirm_message else R.string.m3_watch_confirm_message
                ),
                confirmLabel = stringResource(if (detail.watching) R.string.m3_unwatch else R.string.m3_watch),
                onConfirm = {
                    confirmWatch = false
                    vm.toggleWatch()
                },
                onDismiss = { confirmWatch = false },
            )
        }

        Box(Modifier.fillMaxSize()) {
            // In-page anchors (`#xxx` in a README) must scroll inside this same container: remember its
            // top Y in the window, then convert the README block's window position into a content offset.
            var containerTopInWindow by remember { mutableFloatStateOf(0f) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { containerTopInWindow = it.positionInWindow().y }
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.clickable {
                        onOpenUser(repo.owner?.login ?: owner)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(repo.owner?.avatarUrl, size = 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = repo.owner?.login ?: owner,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = repo.name.ifBlank { name },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                // A fork should show who the upstream is, and link to it.
                val parent = repo.parent
                val parentOwner = parent?.owner?.login
                val parentName = parent?.name
                if (!parentOwner.isNullOrBlank() && !parentName.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.ForkRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.m3_forked_from),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "$parentOwner/$parentName",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onOpenRepo(parentOwner, parentName) },
                        )
                    }
                }
                if (!repo.description.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = repo.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ----- Stat chips (tappable: stars -> stargazers, forks -> fork list)
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!repo.language.isNullOrBlank()) {
                        StatChip(
                            icon = null,
                            value = repo.language,
                            dot = languageColor(context, repo.language),
                            onClick = null,
                        )
                    }
                    StatChip(
                        icon = Icons.Outlined.StarBorder,
                        value = compactNumber(repo.stargazersCount),
                        label = stringResource(R.string.m3_starred_pill),
                        onClick = { onOpenStargazers(owner, name) },
                    )
                    StatChip(
                        icon = Icons.Outlined.ForkRight,
                        value = compactNumber(repo.forksCount),
                        label = stringResource(R.string.m3_forks),
                        onClick = { onOpenForks(owner, name) },
                    )
                    StatChip(
                        icon = Icons.Outlined.Groups,
                        value = compactNumber(repo.watchersCount),
                        label = stringResource(R.string.m3_watchers),
                        onClick = { onOpenWatchers(owner, name) },
                    )
                }

                // ----- Primary actions: star / fork / watch (same look; blue = already done)
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToggleActionButton(
                        active = detail.starring,
                        enabled = !starBusy,
                        icon = if (detail.starring) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        label = stringResource(if (detail.starring) R.string.m3_starred_pill else R.string.m3_star),
                        onClick = { confirmStar = true },
                    )
                    ToggleActionButton(
                        active = forked,
                        enabled = !forkBusy && !forkOwned,
                        icon = Icons.Outlined.ForkRight,
                        label = stringResource(if (forked) R.string.m3_forked else R.string.m3_fork),
                        onClick = {
                            if (forked) {
                                val full = forkFullName
                                if (full != null && "/" in full) {
                                    onOpenRepo(full.substringBefore("/"), full.substringAfter("/"))
                                }
                            } else {
                                showForkDialog = true
                            }
                        },
                    )
                    ToggleActionButton(
                        active = detail.watching,
                        enabled = !watchBusy,
                        icon = if (detail.watching) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        label = stringResource(if (detail.watching) R.string.m3_watching else R.string.m3_watch),
                        onClick = { confirmWatch = true },
                    )
                }

                // ----- Navigation chips (with open counts; unavailable features are greyed out)
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NavChip(
                        icon = Icons.Outlined.Insights,
                        label = stringResource(R.string.m3_activity),
                        onClick = { onOpenActivity(owner, name) },
                    )
                    NavChip(
                        icon = Icons.Outlined.Groups,
                        label = stringResource(R.string.m3_contributors),
                        badge = countBadge(detail.counts.contributors),
                        onClick = { onOpenContributors(owner, name) },
                    )
                    NavChip(
                        icon = null,
                        label = stringResource(R.string.m3_issues),
                        badge = detail.counts.issues.toString(),
                        enabled = repo.hasIssues,
                        onClick = { onOpenIssues(owner, name) },
                    )
                    NavChip(
                        icon = null,
                        label = stringResource(R.string.m3_pulls),
                        badge = detail.counts.pulls.toString(),
                        onClick = { onOpenPulls(owner, name) },
                    )
                    NavChip(
                        icon = Icons.Outlined.CallSplit,
                        label = stringResource(R.string.m3_branch),
                        badge = detail.counts.branches.toString(),
                        onClick = { onOpenBranches(owner, name) },
                    )
                    NavChip(
                        icon = Icons.Outlined.Label,
                        label = stringResource(R.string.m3_tag),
                        onClick = { onOpenTags(owner, name) },
                    )
                    NavChip(
                        icon = Icons.Outlined.NewReleases,
                        label = stringResource(R.string.m3_releases),
                        onClick = { onOpenReleases(owner, name) },
                    )
                    NavChip(
                        icon = Icons.Outlined.QuestionAnswer,
                        label = stringResource(R.string.m3_title_discussions),
                        enabled = repo.hasDiscussions,
                        onClick = { onOpenDiscussions(owner, name) },
                    )
                    NavChip(
                        icon = null,
                        label = stringResource(R.string.m3_title_actions),
                        onClick = { onOpenActions(owner, name) },
                    )
                    NavChip(
                        icon = Icons.Outlined.Label,
                        label = stringResource(R.string.m3_labels),
                        enabled = repo.permissions?.canTriage == true,
                        onClick = { onOpenLabels(owner, name) },
                    )
                    NavChip(
                        icon = null,
                        label = stringResource(R.string.m3_milestones),
                        enabled = repo.permissions?.canTriage == true,
                        onClick = { onOpenMilestones(owner, name) },
                    )
                    NavChip(
                        icon = null,
                        label = stringResource(R.string.m3_code),
                        onClick = { onOpenCode(owner, name) },
                    )
                    NavChip(
                        icon = null,
                        label = "Download ZIP",
                        onClick = {
                            val branch = repo.defaultBranch ?: "main"
                            val url = "https://github.com/$owner/$name/archive/refs/heads/${Uri.encode(branch)}.zip"
                            download(url, "$name-$branch.zip")
                        },
                    )
                }

                // ----- Language composition (a proportional bar plus percentages, like the web)
                if (detail.languages.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.m3_languages), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LanguageBar(detail.languages.map { it.name to it.percent })
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        detail.languages.take(8).forEach { slice ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(9.dp).clip(CircleShape)
                                        .background(languageColor(context, slice.name))
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = slice.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.m3_percent, formatPercent(slice.percent)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // ----- Contributors (avatar + count)
                if (detail.contributors.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.m3_contributors), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        CountBadge(countBadge(detail.counts.contributors) ?: detail.contributors.size.toString())
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onOpenContributors(owner, name) }) {
                            Text(stringResource(R.string.m3_expand))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        detail.contributors.take(16).forEach { user ->
                            Box(
                                Modifier.clip(CircleShape).clickable(enabled = user.login.isNotBlank()) {
                                    onOpenUser(user.login)
                                },
                            ) {
                                Avatar(user.avatarUrl, size = 34.dp)
                            }
                        }
                    }
                }

                val lastPush = repo.pushedAt ?: repo.updatedAt
                if (lastPush != null) {
                    Spacer(Modifier.height(16.dp))
                    MetaText(stringResource(R.string.m3_updated_ago, relativeTime(lastPush)))
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                Text("README", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                if (detail.readmeHtml.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.m3_repo_no_readme),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    HtmlText(
                        html = detail.readmeHtml,
                        baseUrl = repoRawBaseUrl(owner, name, repo.defaultBranch),
                        linkBaseUrl = repoBlobBaseUrl(owner, name, repo.defaultBranch),
                        // Jump to that section of the same page (`#xxx` in a README).
                        onAnchor = { _, yInView, viewTopInWindow ->
                            val target =
                                scrollState.value + (viewTopInWindow + yInView - containerTopInWindow)
                            scope.launch {
                                scrollState.animateScrollTo(target.toInt().coerceAtLeast(0))
                            }
                        },
                    )
                }

                Spacer(Modifier.height(20.dp))
                TextButton(
                    onClick = {
                        val url = repo.htmlUrl ?: "https://github.com/$owner/$name"
                        if (!BrowserLauncher.open(context, url)) linkToCopy = url
                    }
                ) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.m3_view_full_repo))
                }
                Spacer(Modifier.height(24.dp))
            }

            AnimatedVisibility(
                visible = showBackToTop,
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandLess,
                        contentDescription = stringResource(R.string.m3_back_to_top),
                    )
                }
            }
        }
        linkToCopy?.let { url -> LinkCopyDialog(url = url) { linkToCopy = null } }
    }
}

/** Stat chip: dot/icon + value + label; outlined in the primary colour when tappable. */
@Composable
private fun StatChip(
    icon: ImageVector?,
    value: String,
    label: String? = null,
    dot: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (label != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

/** Navigation chip: icon + name + count badge; `enabled=false` greys it out (feature not available). */
@Composable
private fun NavChip(
    icon: ImageVector?,
    label: String,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val container =
        if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow
    val content =
        if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
            Spacer(Modifier.width(6.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content)
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            CountBadge(badge, dim = !enabled)
        }
    }
}

@Composable
private fun CountBadge(text: String, dim: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (dim) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.primaryContainer
            )
            .padding(horizontal = 7.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Language colour bar: fills one row by percentage. */
@Composable
private fun LanguageBar(languages: List<Pair<String, Double>>) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp)),
    ) {
        languages.forEach { (name, percent) ->
            Box(
                Modifier
                    .weight(percent.toFloat().coerceAtLeast(0.5f))
                    .fillMaxHeight()
                    .background(languageColor(context, name)),
            )
        }
    }
}

/** Fork dialog: new name + "default branch only" (a GitHub fork API option). */
@Composable
private fun ForkDialog(
    repoName: String,
    busy: Boolean,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var forkName by remember { mutableStateOf(repoName) }
    var defaultBranchOnly by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m3_fork_confirm_title, repoName)) },
        text = {
            Column {
                Text(stringResource(R.string.m3_fork_confirm_message))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = forkName,
                    onValueChange = { forkName = it },
                    label = { Text(stringResource(R.string.m3_fork_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = defaultBranchOnly, onCheckedChange = { defaultBranchOnly = it })
                    Column {
                        Text(
                            text = stringResource(R.string.m3_fork_default_branch_only),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.m3_fork_default_branch_only_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = forkName.isNotBlank() && !busy,
                onClick = { onConfirm(forkName, defaultBranchOnly) },
            ) { Text(stringResource(R.string.m3_fork_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_cancel)) } },
    )
}

private fun languageColor(context: android.content.Context, language: String): Color =
    Color(ColorsProvider.getColorAsColor(language, context))

private fun formatPercent(value: Double): String =
    if (value >= 10) String.format(java.util.Locale.US, "%.0f", value)
    else String.format(java.util.Locale.US, "%.1f", value)

private fun countBadge(value: Int): String? = when {
    value <= 0 -> null
    value >= 100 -> "99+"
    else -> value.toString()
}
