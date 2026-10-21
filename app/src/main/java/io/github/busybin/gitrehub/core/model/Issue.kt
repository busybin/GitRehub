package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * An issue as every issues endpoint returns it. GitHub's issue endpoints also return pull requests,
 * which is what [pullRequest] marks; the PR-only numbers live in [PullRequest].
 *
 * Search results have no `repository` object and no `repository_url` in the legacy shape, so
 * [repoFullName] falls back to the URL fields the same way the old data layer did.
 */
@Serializable
data class Issue(
    val id: Long = 0,
    val number: Int = 0,
    val title: String? = null,
    val body: String? = null,
    /** `open` or `closed`; kept raw so an unknown value never fails decoding. */
    val state: String = "open",
    val comments: Int = 0,
    val locked: Boolean = false,
    val user: User? = null,
    val assignee: User? = null,
    val assignees: List<User> = emptyList(),
    val labels: List<Label> = emptyList(),
    val milestone: Milestone? = null,
    val reactions: Reactions? = null,
    @SerialName("pull_request") val pullRequest: PullRequestRef? = null,
    @SerialName("repository_url") val repositoryUrl: String? = null,
    val repository: Repo? = null,
    val url: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    @SerialName("closed_at") @Serializable(with = InstantSerializer::class) val closedAt: Instant? = null,
) {
    val isClosed: Boolean get() = state.equals("closed", ignoreCase = true)

    val isPullRequest: Boolean get() = pullRequest != null

    /** `owner/repo`; search results only carry URLs. */
    val repoFullName: String?
        get() = repository?.nameWithOwner?.takeIf { it.contains('/') }
            ?: repoFullNameFromUrl(repositoryUrl)
            ?: repoFullNameFromUrl(url)
            ?: repoFullNameFromUrl(htmlUrl)
            ?: repoFullNameFromUrl(pullRequest?.htmlUrl)
}

/** The mini object attached to an issue that is really a pull request. */
@Serializable
data class PullRequestRef(
    val url: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("diff_url") val diffUrl: String? = null,
    @SerialName("patch_url") val patchUrl: String? = null,
    @SerialName("merged_at") @Serializable(with = InstantSerializer::class) val mergedAt: Instant? = null,
)

@Serializable
data class Label(
    val name: String? = null,
    /** Six hex digits without `#`; the UI picks the text colour from it. */
    val color: String? = null,
    val description: String? = null,
    val url: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is Label && other.name == name

    override fun hashCode(): Int = name?.hashCode() ?: 0
}

@Serializable
data class Milestone(
    val number: Int = 0,
    val title: String? = null,
    val state: String? = null,
    val description: String? = null,
    @SerialName("open_issues") val openIssues: Int = 0,
    @SerialName("closed_issues") val closedIssues: Int = 0,
    @SerialName("due_on") @Serializable(with = InstantSerializer::class) val dueOn: Instant? = null,
)

/** The reaction summary GitHub embeds in an issue/comment; `total_count` is not the sum of fields. */
@Serializable
data class Reactions(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("+1") val plusOne: Int = 0,
    @SerialName("-1") val minusOne: Int = 0,
    val laugh: Int = 0,
    val hooray: Int = 0,
    val confused: Int = 0,
    val heart: Int = 0,
    val rocket: Int = 0,
    val eyes: Int = 0,
)

/** Accepts `https://api.github.com/repos/o/r/...` and `https://github.com/o/r/...`. */
internal fun repoFullNameFromUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val path = raw.substringAfter("/repos/", "").ifEmpty { raw.substringAfter("github.com/", "") }
    if (path.isEmpty()) return null
    val parts = path.trim('/').split('/')
    if (parts.size < 2) return null
    val owner = parts[0]
    val name = parts[1]
    if (owner.isBlank() || name.isBlank()) return null
    return "$owner/$name"
}
