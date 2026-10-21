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
