package io.github.busybin.gitrehub.compose.issues

import io.github.busybin.gitrehub.core.model.MergeResult
import io.github.busybin.gitrehub.core.model.Reaction
import io.github.busybin.gitrehub.core.net.GitHubClient
import io.github.busybin.gitrehub.core.net.WriteBodies
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the request bodies of write operations look like.
 *
 * GitHub accepts only the literal field/enum names, and a field that was not meant to change is
 * rewritten when it is sent (`milestone: null` clears the milestone), so the bodies are built in
 * `core/net/WriteBodies` and asserted here as JSON. A rename breaks here first. The non-ASCII
 * values double as a check that the bodies keep their encoding.
 */
class WriteOpsTest {

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun JsonObject.strings(key: String): List<String> =
        this[key]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

    @Test
    fun mergeMethodsUseGitHubsExactWords() {
        assertEquals("merge", MergeMethod.MERGE.api)
        assertEquals("squash", MergeMethod.SQUASH.api)
        assertEquals("rebase", MergeMethod.REBASE.api)
        // Exactly three; a "squash + keep commits" variant would need UI work too.
        assertEquals(3, MergeMethod.entries.size)
    }

    @Test
    fun mergeBodyCarriesTheMethodAndOnlyARealMessage() {
        val json = WriteBodies.merge(MergeMethod.SQUASH.api, "一起合了")
        assertEquals("squash", json.text("merge_method"))
        assertEquals("一起合了", json.text("commit_message"))
        assertFalse(json.containsKey("sha"))

        assertFalse(WriteBodies.merge(MergeMethod.MERGE.api, null).containsKey("commit_message"))
        assertFalse(WriteBodies.merge(MergeMethod.MERGE.api, "  ").containsKey("commit_message"))
    }

    @Test
    fun stateNamesAreLowercase() {
        assertEquals("closed", WriteBodies.editIssue(state = "closed").text("state"))
        assertEquals("open", WriteBodies.editIssue(state = "open").text("state"))
    }

    @Test
    fun editSendsOnlyTheFieldsWePassed() {
        val json = WriteBodies.editIssue(title = "新标题", body = "新正文")
        assertEquals("新标题", json.text("title"))
        assertEquals("新正文", json.text("body"))
        // Fields we did not pass must not be sent: it would rewrite the current values.
        assertFalse(json.containsKey("state"))
        assertFalse(json.containsKey("milestone"))
        assertFalse(json.containsKey("labels"))
    }

    @Test
    fun clearingTheMilestoneSendsAnExplicitNull() {
        val json = WriteBodies.editIssue(milestone = null, milestoneChanged = true)
        assertTrue(json.containsKey("milestone"))
        assertTrue(json["milestone"] is JsonNull)
        assertFalse(json.containsKey("title"))

        assertEquals("4", WriteBodies.editIssue(milestone = 4, milestoneChanged = true).text("milestone"))
    }

    @Test
    fun createIssueSendsOnlyWhatWasChosen() {
        val json = WriteBodies.createIssue(
            title = "同事",
            body = "正文",
            labels = listOf("bug"),
            assignees = listOf("busybin"),
            milestone = 3,
        )
        assertEquals("同事", json.text("title"))
        assertEquals("正文", json.text("body"))
        assertEquals(listOf("bug"), json.strings("labels"))
        assertEquals(listOf("busybin"), json.strings("assignees"))
        assertEquals("3", json.text("milestone"))

        val bare = WriteBodies.createIssue("只是标题", null, emptyList(), emptyList(), null)
        assertFalse(bare.containsKey("body"))
        assertFalse(bare.containsKey("labels"))
        assertFalse(bare.containsKey("assignees"))
        assertFalse(bare.containsKey("milestone"))
    }

    @Test
    fun commentAndReactionBodiesCarryOneField() {
        assertEquals("你好", WriteBodies.comment("你好").text("body"))
        assertEquals(1, WriteBodies.comment("你好").size)

        assertEquals("hooray", WriteBodies.reaction("hooray").text("content"))
        assertEquals(1, WriteBodies.reaction("hooray").size)
    }

    @Test
    fun assigneeRemovalUsesTheSameShapeAsAddition() {
        assertEquals(listOf("a", "b"), WriteBodies.assignees(listOf("a", "b")).strings("assignees"))
    }

    @Test
    fun reactionContentsUseGitHubsExactWords() {
        // These eight strings are GitHub's `content` enum; a typo ("+1" as "plus_one") is a 422.
        assertEquals("+1", ReactionKind.PLUS_ONE.api)
        assertEquals("-1", ReactionKind.MINUS_ONE.api)
        assertEquals(
            listOf("laugh", "hooray", "confused", "heart", "rocket", "eyes"),
            listOf(
                ReactionKind.LAUGH, ReactionKind.HOORAY, ReactionKind.CONFUSED,
                ReactionKind.HEART, ReactionKind.ROCKET, ReactionKind.EYES,
            ).map { it.api },
        )
        assertEquals(8, ReactionKind.entries.size)
        // The summary's content maps back to the enum (the UI picks icons by content).
        assertEquals(ReactionKind.HEART, ReactionKind.of("heart"))
        assertNull(ReactionKind.of("nope"))
    }

    @Test
    fun reactionsDecodeWhoReacted() {
        val rows = GitHubClient.json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(Reaction.serializer()),
            """[{"id":77,"content":"heart","user":{"login":"busybin","avatar_url":"a.png"}}]""",
        )
        assertEquals(1, rows.size)
        assertEquals(77L, rows.first().id)
        assertEquals("heart", rows.first().content)
        assertEquals("busybin", rows.first().user?.login)
    }

    @Test
    fun aRefusedMergeIsAFailureEvenWithA200() {
        val refused = GitHubClient.json.decodeFromString(
            MergeResult.serializer(),
            """{"merged":false,"message":"Pull request is not mergeable"}""",
        )
        assertFalse(refused.merged)
        assertNull(refused.sha)

        val merged = GitHubClient.json.decodeFromString(
            MergeResult.serializer(),
            """{"merged":true,"sha":"abc123","message":"Pull Request successfully merged"}""",
        )
        assertTrue(merged.merged)
        assertEquals("abc123", merged.sha)
    }
}
