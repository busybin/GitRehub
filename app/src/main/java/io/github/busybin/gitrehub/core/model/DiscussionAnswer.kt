package io.github.busybin.gitrehub.core.model

/**
 * Answer state of a Q&A discussion, read through GraphQL.
 *
 * The REST payload has no idea which comment was accepted, so the screen asks GraphQL for the
 * discussion's node id plus a `databaseId -> node id` map; the REST comment ids then line up with
 * the ids the `markDiscussionCommentAsAnswer` mutation needs.
 */
data class DiscussionAnswerInfo(
    val discussionId: String,
    /** Database id of the accepted comment, null when nothing was accepted yet. */
    val answerCommentId: Long?,
    private val nodeIds: Map<Long, String>,
) {
    val isAnswered: Boolean get() = answerCommentId != null

    fun nodeIdOf(commentId: Long): String? = nodeIds[commentId]
}
