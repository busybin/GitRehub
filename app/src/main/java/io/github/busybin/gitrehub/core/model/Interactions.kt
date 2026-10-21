package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.math.abs
import java.time.Instant

/**
 * "What others did to my repos": stars, forks, PRs and issues, read from one GraphQL query.
 *
 * The received-events stream only covers watched repos and GitHub does not auto-watch repos you own,
 * so these never show up there; asking the repos directly avoids that dependency. Responses are
 * decoded into [Event] rows with negative synthetic ids, which can never collide with a real GitHub
 * event id and stay stable across refreshes (so the shared read state keeps working).
 */
internal fun parseInteractions(root: JsonElement, format: Json): List<Event> {
    val viewer = runCatching { format.decodeFromJsonElement(InteractionEnvelope.serializer(), root) }
        .getOrNull()?.viewer ?: return emptyList()
    val me = viewer.login.takeIf { it.isNotBlank() } ?: return emptyList()

    val out = mutableListOf<Event>()
    for (repoNode in viewer.repositories?.nodes.orEmpty()) {
        val fullName = repoNode.nameWithOwner.takeIf { it.contains('/') } ?: continue
        val repo = EventRepo(
            name = fullName.substringAfter('/'),
            fullName = fullName,
        )

        repoNode.stargazers?.edges.orEmpty().forEach { edge ->
            val at = edge.starredAt ?: return@forEach
            val who = edge.node ?: return@forEach
            if (who.login.isBlank() || who.login == me) return@forEach
            out += synthesized(EventType.WatchEvent, who, repo, at, me)
        }

        repoNode.forks?.nodes.orEmpty().forEach { fork ->
            val at = fork.createdAt ?: return@forEach
            val who = fork.owner ?: return@forEach
            if (who.login.isBlank() || who.login == me) return@forEach
            val forkedFullName = fork.nameWithOwner.takeIf { it.contains('/') }
            out += synthesized(
                type = EventType.ForkEvent,
                actor = who,
                repo = repo,
                at = at,
                viewer = me,
                forkee = forkedFullName?.let { EventRepo(name = it.substringAfter('/'), fullName = it) },
            )
        }

        // GraphQL `issues` also returns pull requests; skip those numbers or each PR appears twice.
        val pullRequestNumbers = repoNode.pullRequests?.nodes.orEmpty()
            .mapNotNull { it.number.takeIf { number -> number > 0 } }
            .toSet()

        repoNode.pullRequests?.nodes.orEmpty().forEach { pull ->
            val at = pull.createdAt ?: return@forEach
            val who = pull.author ?: return@forEach
            if (who.login.isBlank() || who.login == me) return@forEach
            out += synthesized(
                type = EventType.PullRequestEvent,
                actor = who,
                repo = repo,
                at = at,
                viewer = me,
                // The row is stamped at creation time, so the verb is always "opened".
                action = "opened",
                pullRequest = EventSubject(number = pull.number, title = pull.title),
            )
        }

        repoNode.issues?.nodes.orEmpty().forEach { issue ->
            if (issue.number <= 0 || issue.number in pullRequestNumbers) return@forEach
            val at = issue.createdAt ?: return@forEach
            val who = issue.author ?: return@forEach
            if (who.login.isBlank() || who.login == me) return@forEach
            out += synthesized(
                type = EventType.IssuesEvent,
                actor = who,
                repo = repo,
                at = at,
                viewer = me,
                action = "opened",
                issue = EventSubject(number = issue.number, title = issue.title),
            )
        }
    }
    return out
}

private fun synthesized(
    type: EventType,
    actor: InteractionUser,
    repo: EventRepo,
    at: Instant,
    viewer: String,
    action: String? = null,
    pullRequest: EventSubject? = null,
    issue: EventSubject? = null,
    forkee: EventRepo? = null,
) = Event(
    id = syntheticId(type.name, actor.login, repo.fullName, at.toString(), action, pullRequest?.number?.toString()),
    type = type,
    createdAt = at,
    actor = EventActor(login = actor.login, avatarUrl = actor.avatarUrl),
    repo = repo,
    payload = EventPayload(
        action = action,
        pullRequest = pullRequest,
        issue = issue,
        forkee = forkee,
    ),
)

/** Stable negative id: it can never collide with a real event id, and the same event always hashes the same value. */
private fun syntheticId(vararg parts: String?): Long =
    -(abs(parts.joinToString("|").hashCode().toLong()) + 1)

@Serializable
private data class InteractionEnvelope(val viewer: InteractionViewer? = null)

@Serializable
private data class InteractionViewer(
    val login: String = "",
    val repositories: InteractionRepos? = null,
)

@Serializable
private data class InteractionRepos(val nodes: List<InteractionRepo> = emptyList())

@Serializable
private data class InteractionRepo(
    @SerialName("nameWithOwner") val nameWithOwner: String = "",
    val stargazers: InteractionStargazers? = null,
    val forks: InteractionForks? = null,
    val pullRequests: InteractionSubjects? = null,
    val issues: InteractionSubjects? = null,
)

@Serializable
private data class InteractionStargazers(val edges: List<InteractionStarEdge> = emptyList())

@Serializable
private data class InteractionStarEdge(
    @SerialName("starredAt") @Serializable(with = InstantSerializer::class) val starredAt: Instant? = null,
    val node: InteractionUser? = null,
)

@Serializable
private data class InteractionForks(val nodes: List<InteractionFork> = emptyList())

@Serializable
private data class InteractionFork(
    @SerialName("nameWithOwner") val nameWithOwner: String = "",
    @SerialName("createdAt") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val owner: InteractionUser? = null,
)

@Serializable
private data class InteractionSubjects(val nodes: List<InteractionSubject> = emptyList())

@Serializable
private data class InteractionSubject(
    val number: Int = 0,
    val title: String? = null,
    @SerialName("createdAt") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val author: InteractionUser? = null,
)

@Serializable
private data class InteractionUser(
    val login: String = "",
    @SerialName("avatarUrl") val avatarUrl: String? = null,
)
