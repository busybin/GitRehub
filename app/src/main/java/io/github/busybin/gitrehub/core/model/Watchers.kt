package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * `repository.watchers` over GraphQL. REST `/subscribers` answers 404 for repositories the account
 * does not administer, while GraphQL reads the watcher list of every public repository.
 */
internal fun parseWatchers(root: JsonElement, format: Json): WatchersPage {
    val watchers = runCatching {
        format.decodeFromJsonElement(WatchersEnvelope.serializer(), root)
    }.getOrNull()?.repository?.watchers ?: return WatchersPage()
    return WatchersPage(
        users = watchers.nodes.map { node ->
            User(
                // `databaseId` is null for apps and bots; the login hash keeps list keys unique.
                id = node.databaseId ?: node.login.hashCode().toLong(),
                login = node.login,
                name = node.name,
                avatarUrl = node.avatarUrl,
            )
        },
        hasNext = watchers.pageInfo?.hasNextPage == true,
        cursor = watchers.pageInfo?.endCursor,
    )
}

@Serializable
private data class WatchersEnvelope(val repository: WatchersRepository? = null)

@Serializable
private data class WatchersRepository(val watchers: WatchersConnection? = null)

@Serializable
private data class WatchersConnection(
    val nodes: List<WatchersNode> = emptyList(),
    val pageInfo: WatchersPageInfo? = null,
)

@Serializable
private data class WatchersPageInfo(
    @SerialName("hasNextPage") val hasNextPage: Boolean = false,
    @SerialName("endCursor") val endCursor: String? = null,
)

@Serializable
private data class WatchersNode(
    @SerialName("databaseId") val databaseId: Long? = null,
    val login: String = "",
    val name: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null,
)
