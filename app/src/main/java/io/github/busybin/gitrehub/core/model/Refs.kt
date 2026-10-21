package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A branch (`GET /repos/{o}/{r}/branches`). [commit] carries only sha/url here, not a full commit.
 */
@Serializable
data class Branch(
    val name: String = "",
    val commit: Commit? = null,
    @SerialName("protected") val isProtected: Boolean = false,
)

/** A tag (`GET /repos/{o}/{r}/tags`); same shape as a branch, tags are never "protected". */
@Serializable
data class Tag(
    val name: String = "",
    val commit: Commit? = null,
)
