package io.github.busybin.gitrehub.compose.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.relativeTime
import io.github.busybin.gitrehub.core.model.Comment
import io.github.busybin.gitrehub.core.model.TimelineEvent

/**
 * One row of the merged thread: either a comment or a state change.
 *
 * The issue screen used to render "comments only", so who closed a thread, who added a label or
 * where it was referenced from were invisible. [mergeTimeline] sorts both sources by time; the
 * screen renders the result with a single `items(...)`.
 */
data class TimelineRow(
    val at: Long,
    val key: String,
    val comment: Comment? = null,
    val event: TimelineEvent? = null,
)

/** Events we can phrase in every language; anything else is dropped instead of shown raw. */
private val SUPPORTED_EVENTS = setOf(
    "closed", "reopened", "labeled", "unlabeled", "assigned", "unassigned",
    "milestoned", "demilestoned", "renamed", "referenced", "cross-referenced",
    "merged", "review_requested", "converted_to_draft", "ready_for_review",
    "locked", "unlocked",
)

/**
 * Merge comments and timeline events into one ascending list.
 *
 * Ties are broken by key so the order is stable across recompositions; events without a timestamp
 * keep their payload order after the comment they follow (they are rare and only cosmetic).
 */
fun mergeTimeline(comments: List<Comment>, events: List<TimelineEvent>): List<TimelineRow> {
    val rows = ArrayList<TimelineRow>(comments.size + events.size)
    comments.forEach { comment ->
        rows += TimelineRow(
            at = comment.createdAt?.toEpochMilli() ?: 0L,
            key = "c${comment.id}",
            comment = comment,
        )
    }
    events.filter { it.event in SUPPORTED_EVENTS }.forEach { event ->
        rows += TimelineRow(
            at = event.createdAt?.toEpochMilli() ?: 0L,
            key = "e${event.id}:${event.event}",
            event = event,
        )
    }
    return rows.sortedWith(compareBy({ it.at }, { it.key }))
}

@Composable
fun TimelineEventRow(event: TimelineEvent) {
    val actor = event.actor?.login.orEmpty().ifBlank { stringResource(R.string.m3_tl_someone) }
    val text = eventText(event, actor) ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = relativeTime(event.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun eventText(event: TimelineEvent, actor: String): String? = when (event.event) {
    "closed" -> stringResource(R.string.m3_tl_closed, actor)
    "reopened" -> stringResource(R.string.m3_tl_reopened, actor)
    "labeled" -> event.label?.name?.let { stringResource(R.string.m3_tl_labeled, actor, it) }
    "unlabeled" -> event.label?.name?.let { stringResource(R.string.m3_tl_unlabeled, actor, it) }
    "assigned" -> stringResource(R.string.m3_tl_assigned, actor, event.assignee?.login.orEmpty())
    "unassigned" -> stringResource(R.string.m3_tl_unassigned, actor, event.assignee?.login.orEmpty())
    "milestoned" -> event.milestone?.title?.let { stringResource(R.string.m3_tl_milestoned, actor, it) }
    "demilestoned" -> event.milestone?.title?.let { stringResource(R.string.m3_tl_demilestoned, actor, it) }
    "renamed" -> stringResource(
        R.string.m3_tl_renamed,
        actor,
        event.rename?.from.orEmpty(),
        event.rename?.to.orEmpty(),
    )
    "referenced" -> stringResource(
        R.string.m3_tl_referenced,
        actor,
        event.source?.issue?.reference.orEmpty(),
    )
    "cross-referenced" -> stringResource(
        R.string.m3_tl_cross_referenced,
        actor,
        event.source?.issue?.reference.orEmpty(),
    )
    "merged" -> stringResource(R.string.m3_tl_merged, actor)
    "review_requested" -> stringResource(
        R.string.m3_tl_review_requested,
        actor,
        event.requestedReviewer?.login.orEmpty(),
    )
    "converted_to_draft" -> stringResource(R.string.m3_tl_draft, actor)
    "ready_for_review" -> stringResource(R.string.m3_tl_ready, actor)
    "locked" -> stringResource(R.string.m3_tl_locked, actor)
    "unlocked" -> stringResource(R.string.m3_tl_unlocked, actor)
    else -> null
}
