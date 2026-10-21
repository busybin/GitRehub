package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Notification threads as `GET /notifications` returns them; every key here is load-bearing. */
class NotificationJsonTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `decodes a thread with its subject repository and reason`() {
        val body = """
            {
              "id": "918273645",
              "unread": true,
              "reason": "review_requested",
              "updated_at": "2026-10-20T09:00:00Z",
              "last_read_at": null,
              "subject": {
                "title": "feat: incremental index rebuild",
                "url": "https://api.github.com/repos/northwind/halcyon/pulls/511",
                "latest_comment_url": "https://api.github.com/repos/northwind/halcyon/issues/comments/9004",
                "type": "PullRequest"
              },
              "repository": {
                "id": 42,
                "name": "halcyon",
                "full_name": "northwind/halcyon",
                "owner": {"login": "northwind", "avatar_url": "https://x/n.png"}
              },
              "url": "https://api.github.com/notifications/threads/918273645",
              "subscription_url": "https://api.github.com/notifications/threads/918273645/subscription"
            }
        """.trimIndent()

        val thread = json.decodeFromString(Notification.serializer(), body)
        assertEquals("918273645", thread.id)
        assertEquals(918273645L, thread.numericId)
        assertTrue(thread.unread)
        assertEquals("review_requested", thread.reason)
        assertEquals("PullRequest", thread.subject?.type)
        assertEquals("northwind/halcyon", thread.repoFullName)
        assertEquals("https://api.github.com/repos/northwind/halcyon/issues/comments/9004", thread.subject?.latestCommentUrl)
        assertEquals(Instant.parse("2026-10-20T09:00:00Z"), thread.updatedAt)
        assertNull(thread.lastReadAt)
    }

    @Test
    fun `a non-numeric id still decodes and yields a stable number`() {
        val thread = json.decodeFromString(
            Notification.serializer(),
            """{"id": "n1", "unread": false, "reason": "ci_activity", "subject": {"type": "CheckSuite"}}""",
        )
        assertEquals("n1", thread.id)
        assertEquals("n1".hashCode().toLong(), thread.numericId)
        assertFalse(thread.unread)
        // Threads without a repository (org invitations, ...) must not produce a blank repo name.
        assertNull(thread.repoFullName)
    }

    @Test
    fun `last_read_at is optional`() {
        val body = """
            {
              "id": "1",
              "unread": false,
              "reason": "author",
              "last_read_at": "2026-10-19T08:00:00Z",
              "subject": {"title": "v2.3.1", "type": "Release"}
            }
        """.trimIndent()
        val thread = json.decodeFromString(Notification.serializer(), body)
        assertEquals(Instant.parse("2026-10-19T08:00:00Z"), thread.lastReadAt)
        assertNull(thread.subject?.url)
    }
}
