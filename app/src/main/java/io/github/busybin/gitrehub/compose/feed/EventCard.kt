package io.github.busybin.gitrehub.compose.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.MetaText
import io.github.busybin.gitrehub.compose.common.Pill
import io.github.busybin.gitrehub.compose.common.UiTokens
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.model.Event

/**
 * Where an event card leads when tapped; shared by the feed and the repo activity timeline
 * so both render the exact same card.
 */
internal sealed interface FeedTarget {
    /** Fork events open the repository it was forked into. */
    data class ForkedRepo(val fullName: String) : FeedTarget

    /** Issue/PR events open the matching detail page. */
    data class Issue(val owner: String, val repo: String, val number: Int) : FeedTarget

    data class Releases(val owner: String, val repo: String) : FeedTarget

    data class Repo(val fullName: String) : FeedTarget

    /** Events without a repo (e.g. someone followed me) fall back to the actor's profile. */
    data class User(val login: String) : FeedTarget
}

/**
 * Where tapping the card body leads; order is priority, the most specific target wins.
 * Must use [Event.repoFullName]: the event stream's mini `repo` object has no `fullName`,
 * so reading it made the target always null.
 */
internal fun Event.feedTarget(): FeedTarget? {
    val payload = this.payload
    (payload.forkee?.fullName ?: payload.forkee?.name)?.takeIf { it.contains('/') }
        ?.let { return FeedTarget.ForkedRepo(it) }
    val full = repoFullName
    val number = payload.pullRequest?.number?.takeIf { it > 0 }
        ?: payload.issue?.number?.takeIf { it > 0 }
    if (full != null && number != null) {
        val owner = full.substringBefore('/')
        val name = full.substringAfter('/')
        if (owner.isNotBlank() && name.isNotBlank()) return FeedTarget.Issue(owner, name, number)
    }
    if (full != null && payload.release != null) {
        val owner = full.substringBefore('/')
        val name = full.substringAfter('/')
        if (owner.isNotBlank() && name.isNotBlank()) return FeedTarget.Releases(owner, name)
    }
    if (full != null) return FeedTarget.Repo(full)
    return actor?.login?.takeIf { it.isNotBlank() }?.let { FeedTarget.User(it) }
}

/**
 * One feed card, shared by the global feed and the repo activity page. Three distinct tap zones:
 * avatar/username -> [onOpenUser]; the repo line -> [onOpenRepo]; the rest -> [onOpen] (preferred target).
 */
@Composable
internal fun EventCard(
    event: Event,
    tokens: UiTokens,
    isRead: Boolean,
    onOpenUser: (String) -> Unit,
    onOpenRepo: (String) -> Unit,
    onOpen: (Event) -> Unit,
) {
    val verbLabel = stringResource(event.verb().labelRes)
    val verbNumber = event.numberLabel()
    val verb = if (verbNumber != null) "$verbLabel $verbNumber" else verbLabel
    val title = event.titleLine()
    val repo = event.repoFullName ?: event.repo?.name
    val target = event.feedTarget()
    val iconRes = event.type.drawableRes
    val actor = event.actor?.login

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isRead) 0.55f else 1f)
            .clickable(enabled = target != null) { onOpen(event) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = tokens.cardShape,
    ) {
        Row(
            modifier = Modifier.padding(tokens.cardPadding),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = actor != null) { actor?.let(onOpenUser) },
            ) {
                Avatar(event.actor?.avatarUrl, size = tokens.avatarSize)
            }
            Spacer(Modifier.width(if (tokens.compact) 8.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = actor ?: "unknown",
                        style = if (tokens.compact) MaterialTheme.typography.bodyLarge
                        else MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(enabled = actor != null) { actor?.let(onOpenUser) },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = verb,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    if (isRead) {
                        Spacer(Modifier.width(6.dp))
                        Pill(stringResource(R.string.m3_read_pill))
                    }
                }
                if (!repo.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = repo,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onOpenRepo(repo) },
                    )
                }
                // PR/issue title; usually null on the trimmed event payload, only synthesized entries have it.
                if (!title.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(if (tokens.compact) 2.dp else 6.dp))
                MetaText(relativeTime(event.createdAt))
            }
            if (iconRes != 0) {
                Box(
                    modifier = Modifier
                        .size(tokens.badgeSize)
                        .clip(RoundedCornerShape(if (tokens.compact) 8.dp else 10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(if (tokens.compact) 15.dp else 18.dp),
                    )
                }
            }
        }
    }
}
