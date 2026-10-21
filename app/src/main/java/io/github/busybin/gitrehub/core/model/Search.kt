package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Envelope every search endpoint answers with. Unlike the list endpoints this is an object,
 * so it needs its own serializer (see `GitHubApi.searchRepos` and friends).
 */
@Serializable
data class SearchResult<T>(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("incomplete_results") val incomplete: Boolean = false,
    val items: List<T> = emptyList(),
)

/** A code search hit: the path inside [repository], plus the blob id the file viewer needs. */
@Serializable
data class SearchCode(
    val name: String? = null,
    val path: String? = null,
    val sha: String? = null,
    val url: String? = null,
    @SerialName("git_url") val gitUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val repository: Repo? = null,
    val score: Double = 0.0,
) {
    /** Code search always embeds the repository, but a fork/private hit may omit `full_name`. */
    val repoFullName: String?
        get() = repository?.nameWithOwner?.takeIf { it.contains('/') }
}
