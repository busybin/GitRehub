package io.github.busybin.gitrehub.compose.data

import io.github.busybin.gitrehub.core.net.GitHubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One of the repositories a profile pins to its overview. Only what the card shows. */
data class PinnedRepo(
    val nameWithOwner: String,
    val description: String?,
    val stars: Int,
    val language: String?,
)

/**
 * Pinned repositories of a user profile, read with GraphQL: REST has no endpoint for them.
 *
 * Organizations pin repositories through a different root field, and the profile page already
 * knows it is looking at an organization, so [load] is only called for users.
 */
object PinnedRepos {

    private const val LIMIT = 6

    private val query = """
        query Pinned(${'$'}login: String!) {
          user(login: ${'$'}login) {
            pinnedItems(first: $LIMIT, types: [REPOSITORY]) {
              nodes {
                ... on Repository {
                  nameWithOwner
                  description
                  stargazerCount
                  primaryLanguage { name }
                }
              }
            }
          }
        }
    """.trimIndent()

    suspend fun load(login: String): List<PinnedRepo> = withContext(Dispatchers.IO) {
        if (login.isBlank()) return@withContext emptyList()
        val data = GitHubClient.graphQl(query, buildJsonObject { put("login", login) })
        parse(data)
    }

    /** Pure mapping, unit-tested: `data` object → cards, skipping anything unusable. */
    fun parse(data: JsonObject): List<PinnedRepo> {
        val user = data["user"] as? JsonObject ?: return emptyList()
        val nodes = (user["pinnedItems"] as? JsonObject)?.get("nodes") as? JsonArray
            ?: return emptyList()
        return nodes.mapNotNull { node ->
            val repo = node as? JsonObject ?: return@mapNotNull null
            val name = (repo["nameWithOwner"] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            PinnedRepo(
                nameWithOwner = name,
                description = repo.stringOrNull("description"),
                stars = repo["stargazerCount"]?.jsonPrimitive?.intOrNull ?: 0,
                language = (repo["primaryLanguage"] as? JsonObject)?.get("name")?.jsonPrimitive?.content,
            )
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotBlank() }
}
