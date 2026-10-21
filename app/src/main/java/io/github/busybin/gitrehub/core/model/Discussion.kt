package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Category a discussion was filed under ("Q&A", "Ideas", …). */
@Serializable
data class DiscussionCategory(
    val name: String? = null,
    val emoji: String? = null,
    @SerialName("is_answerable") val isAnswerable: Boolean = false,
)

/**
 * One repository discussion.
 *
 * Mirrors the REST object the web UI reads. Everything is optional: this endpoint is not part of
 * GitHub's published REST reference, so it is treated as "best effort, never crash".
 */
@Serializable
data class Discussion(
    val number: Int = 0,
    val title: String? = null,
    val body: String? = null,
    val state: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val comments: Int = 0,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    @SerialName("answer_chosen_at") @Serializable(with = InstantSerializer::class) val answerChosenAt: Instant? = null,
    val user: User? = null,
    val category: DiscussionCategory? = null,
) {
    val isClosed: Boolean get() = state.equals("closed", ignoreCase = true)

    val isAnswered: Boolean get() = answerChosenAt != null
}

@Serializable
data class DiscussionComment(
    val id: Long = 0,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    @SerialName("author_association") val authorAssociation: String? = null,
    val user: User? = null,
)
