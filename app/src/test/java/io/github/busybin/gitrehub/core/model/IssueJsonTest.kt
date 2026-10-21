package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Issue/PR/comment JSON is read straight from GitHub; a wrong key decodes to a silent null. */
class IssueJsonTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `decodes an issue with labels milestone and reactions`() {
        val body = """
            {
              "id": 700482,
              "number": 482,
              "title": "Sync stalls",
              "body": "**What happens**",
              "state": "open",
              "comments": 4,
              "locked": false,
              "user": {"id": 7, "login": "priya", "avatar_url": "https://x/p.png"},
              "assignees": [{"login": "kenji"}],
              "labels": [{"name": "bug", "color": "d73a4a"}],
              "milestone": {"number": 3, "title": "2.4", "open_issues": 12, "closed_issues": 27},
              "reactions": {"total_count": 7, "+1": 3, "-1": 0, "heart": 2},
              "pull_request": null,
              "repository_url": "https://api.github.com/repos/northwind/halcyon",
              "html_url": "https://github.com/northwind/halcyon/issues/482",
              "created_at": "2026-10-16T09:00:00Z",
              "updated_at": "2026-10-20T09:00:00Z"
            }
        """.trimIndent()
        val issue = json.decodeFromString(Issue.serializer(), body)
        assertEquals(482, issue.number)
        assertEquals("priya", issue.user?.login)
        assertEquals(4, issue.comments)
        assertFalse(issue.isClosed)
        assertFalse(issue.isPullRequest)
        assertEquals(listOf("bug"), issue.labels.map { it.name })
        assertEquals(3, issue.milestone?.number)
        assertEquals(2, issue.reactions?.heart)
        assertEquals(3, issue.reactions?.plusOne)
        assertEquals(Instant.parse("2026-10-16T09:00:00Z"), issue.createdAt)
        // The list is fetched per repository and never embeds the repo object.
        assertNull(issue.repository)
        assertEquals("northwind/halcyon", issue.repoFullName)
    }

    @Test
    fun `a pull request in the issues list is marked and has no repository object`() {
        val issue = json.decodeFromString(
            Issue.serializer(),
            """{"id":1,"number":511,"state":"closed","pull_request":{"html_url":"https://github.com/o/r/pull/511"}}""",
        )
        assertTrue(issue.isPullRequest)
        assertTrue(issue.isClosed)
        assertEquals("o/r", issue.repoFullName)
    }

    @Test
    fun `search results resolve the repository from the html url`() {
        val issue = json.decodeFromString(
            Issue.serializer(),
            """{"id":2,"number":9,"html_url":"https://github.com/someone/thing/issues/9"}""",
        )
        assertNull(issue.repository)
        assertEquals("someone/thing", issue.repoFullName)
    }

    @Test
    fun `decodes a pull request with its merge state and diff size`() {
        val pr = json.decodeFromString(
            PullRequest.serializer(),
            """
            {
              "id": 5011,
              "number": 511,
              "state": "open",
              "merged": false,
              "mergeable": true,
              "draft": false,
              "commits": 6,
              "additions": 412,
              "deletions": 88,
              "changed_files": 14,
              "review_comments": 5,
              "base": {"ref": "main", "sha": "abc"},
              "head": {"ref": "feat/index", "sha": "def"}
            }
            """.trimIndent(),
        )
        assertEquals(511, pr.number)
        assertEquals(412, pr.additions)
        assertEquals(88, pr.deletions)
        assertEquals(14, pr.changedFiles)
        assertEquals("main", pr.base?.ref)
        assertEquals("feat/index", pr.head?.ref)
        assertFalse(pr.merged)
    }

    @Test
    fun `decodes a comment with its author association`() {
        val comment = json.decodeFromString(
            Comment.serializer(),
            """
            {
              "id": 9001,
              "body": "Thanks",
              "user": {"login": "kenji"},
              "author_association": "MEMBER",
              "reactions": {"total_count": 4, "+1": 2, "heart": 2},
              "created_at": "2026-10-17T10:00:00Z"
            }
            """.trimIndent(),
        )
        assertEquals(9001, comment.id)
        assertEquals("MEMBER", comment.authorAssociation)
        assertEquals(2, comment.reactions?.heart)
        assertEquals(Instant.parse("2026-10-17T10:00:00Z"), comment.createdAt)
    }
}
