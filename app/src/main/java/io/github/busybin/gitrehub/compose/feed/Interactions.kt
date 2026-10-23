package io.github.busybin.gitrehub.compose.feed

import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.EventType

/**
 * Content fingerprint used to deduplicate the synthetic "actions on my repos" rows against the real
 * events stream: synthetic ids live in a different space, so only content can be compared.
 */
internal fun Event.interactionSignature(): String? = when (type) {
    EventType.WatchEvent, EventType.ForkEvent ->
        "$type|${actor?.login}|$repoFullName"
    EventType.PullRequestEvent, EventType.IssuesEvent ->
        "$type|${actor?.login}|$repoFullName|${numberLabel()}"
    else -> null
}

/**
 * The feed's first page: the event streams plus the "actions on my repos" snapshot, newest first.
 *
 * Rows that both sources carry are dropped on content ([interactionSignature]), because the synthetic
 * interactions live in a different id space; the rest is de-duplicated on id.
 */
internal fun mergeFeedEvents(events: List<Event>, interactions: List<Event>): List<Event> {
    val inEventStream = events.mapNotNullTo(mutableSetOf()) { it.interactionSignature() }
    return (events + interactions.filterNot { it.interactionSignature() in inEventStream })
        .distinctBy { it.id }
        .sortedByDescending { it.createdAt }
}
