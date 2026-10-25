package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A repository as every repo endpoint returns it (`GET /repos/{o}/{r}`, the lists, search, forks).
 *
 * Plain DTO, no ObjectBox: `parent` is the upstream a fork came from, `permissions` is only present
 * when the token can act on the repository.
 */
@Serializable
data class Repo(
    val id: Long = 0,
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("private") val isPrivate: Boolean = false,
    val fork: Boolean = false,
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val url: String? = null,
    val homepage: String? = null,
    val language: String? = null,
    val size: Long = 0,
    @SerialName("stargazers_count") val stargazersCount: Long = 0,
    @SerialName("forks_count") val forksCount: Long = 0,
    @SerialName("subscribers_count") val watchersCount: Long = 0,
    @SerialName("open_issues_count") val openIssuesCount: Long = 0,
    @SerialName("default_branch") val defaultBranch: String? = null,
    @SerialName("has_issues") val hasIssues: Boolean = false,
    @SerialName("has_discussions") val hasDiscussions: Boolean = false,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    @SerialName("pushed_at") @Serializable(with = InstantSerializer::class) val pushedAt: Instant? = null,
    val owner: User? = null,
    /** Present only on forks: the repository this one was forked from. */
    val parent: Repo? = null,
    val license: RepoLicense? = null,
    val topics: List<String> = emptyList(),
    val permissions: RepoPermissions? = null,
) {
    /** `owner/repo`; falls back to the owner when an endpoint omits `full_name`. */
    val nameWithOwner: String
        get() = fullName.takeIf { it.contains('/') }
            ?: listOfNotNull(owner?.login?.takeIf { it.isNotBlank() }, name).joinToString("/")
}

@Serializable
data class RepoLicense(
    val key: String? = null,
    val name: String? = null,
    @SerialName("spdx_id") val spdxId: String? = null,
    val url: String? = null,
)

@Serializable
data class RepoPermissions(
    val admin: Boolean = false,
    val maintain: Boolean = false,
    val push: Boolean = false,
    val triage: Boolean = false,
    val pull: Boolean = false,
) {
    val canWrite: Boolean get() = admin || maintain || push

    val canTriage: Boolean get() = canWrite || triage
}

/**
 * `GET /repos/{o}/{r}/subscription` — GitHub's "watch" state. The API has only two levels on top of
 * "not watching": everything, and ignored (all notifications blocked).
 */
@Serializable
data class RepoSubscription(
    val subscribed: Boolean = false,
    val ignored: Boolean = false,
)

/** What a watch button tap should send; `null` means unwatch. */
enum class WatchLevel { ALL, IGNORED }

/** One page of GraphQL watchers; `cursor` is opaque and only fed back into the next query. */
data class WatchersPage(
    val users: List<User> = emptyList(),
    val hasNext: Boolean = false,
    val cursor: String? = null,
)
