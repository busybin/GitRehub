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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.Avatar
import io.github.busybin.gitrehub.compose.common.Hairline
import io.github.busybin.gitrehub.compose.common.Identifier
import io.github.busybin.gitrehub.compose.common.MetaText
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
 * One feed row, shared by the global feed and the repo activity page. Three distinct tap zones:
 * avatar/username -> [onOpenUser]; the repo line -> [onOpenRepo]; the rest -> [onOpen].
 *
 * Flat row on the page surface with one hairline between rows: the avatar and the unread dot carry
 * the left column, the event icon sits in a fixed right column so every verb lines up. Unread is a
 * 6dp accent dot, not a badge, and read rows drop to the muted text colour instead of fading.
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
    // Three readable roles per row: bright sans for who did it, dim sans for what they did, and the
    // repository in monospace accent (it is the one link in the row). A read row multiplies all of
    // them down instead of fading the whole block, so the colours keep their relationship.
    val level = if (isRead) 0.5f else 1f
    val actorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = level)
    val verbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = level)
    val repoColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isRead) 0.6f else 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = target != null) { onOpen(event) },
    ) {
        Row(
            modifier = Modifier.padding(
                start = tokens.pagePadding,
                end = tokens.pagePadding,
                top = tokens.space3,
                bottom = tokens.space3,
            ),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = actor != null) { actor?.let(onOpenUser) },
            ) {
                Avatar(event.actor?.avatarUrl, size = tokens.avatarSize)
            }
            Spacer(Modifier.width(tokens.space3))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isRead) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(tokens.space2))
                    }
                    Text(
                        text = actor ?: "unknown",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = actorColor,
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
                        color = verbColor,
                        maxLines = 1,
                    )
                }
                if (!repo.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Identifier(
                        text = repo,
                        style = MaterialTheme.typography.bodyMedium,
                        emphasized = true,
                        maxLines = 1,
                        color = repoColor,
                        modifier = Modifier.clickable { onOpenRepo(repo) },
                    )
                }
                if (!title.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = level),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(tokens.space1))
                MetaText(
                    relativeTime(event.createdAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = level),
                )
            }
            if (iconRes != 0) {
                Spacer(Modifier.width(tokens.space3))
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = level),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(tokens.iconSmall),
                )
            }
        }
        Hairline()
    }
}
