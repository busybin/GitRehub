package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * `PullRequest.autoMergeRequest` from GraphQL: GitHub merges the PR once the required checks pass.
 *
 * REST has no field for it at all, so the PR detail screen reads this through [io.github.busybin.gitrehub.core.net.GitHubApi.autoMergeRequest].
 */
@Serializable
data class AutoMergeRequest(
    @SerialName("enabled_at") @Serializable(with = InstantSerializer::class) val enabledAt: Instant? = null,
    @SerialName("merge_method") val mergeMethod: String? = null,
)
