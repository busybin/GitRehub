package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** One issue/PR comment. `authorAssociation` (OWNER/MEMBER/CONTRIBUTOR/...) drives the badge. */
@Serializable
data class Comment(
    val id: Long = 0,
    val body: String? = null,
    val user: User? = null,
    @SerialName("author_association") val authorAssociation: String? = null,
    val reactions: Reactions? = null,
    val url: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
)
