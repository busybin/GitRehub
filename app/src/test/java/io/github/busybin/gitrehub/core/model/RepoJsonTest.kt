package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** `Repo` is decoded straight from GitHub's snake_case JSON; every rename here is a silent bug. */
class RepoJsonTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `decodes the fields the detail page reads`() {
        val body = """
            {
              "id": 1296269,
              "name": "hello-world",
              "full_name": "octocat/hello-world",
              "private": false,
              "fork": true,
              "description": "My first repository",
              "html_url": "https://github.com/octocat/hello-world",
              "language": "Kotlin",
              "stargazers_count": 80,
              "forks_count": 9,
              "subscribers_count": 4,
              "open_issues_count": 12,
              "default_branch": "trunk",
              "has_issues": true,
              "has_discussions": true,
              "pushed_at": "2026-10-19T12:00:00Z",
              "updated_at": "2026-10-20T08:30:00Z",
              "owner": {"id": 1, "login": "octocat", "avatar_url": "https://x/o.png"},
              "parent": {"id": 2, "name": "up", "full_name": "up/up",
                         "owner": {"login": "up"}},
              "license": {"key": "mit", "name": "MIT License", "spdx_id": "MIT"},
              "topics": ["android", "kotlin"],
              "permissions": {"admin": false, "maintain": false, "push": true, "pull": true}
            }
        """.trimIndent()
        val repo = json.decodeFromString(Repo.serializer(), body)
        assertEquals("octocat/hello-world", repo.fullName)
        assertEquals("octocat", repo.owner?.login)
        assertEquals("Kotlin", repo.language)
        assertEquals(80L, repo.stargazersCount)
        assertEquals(9L, repo.forksCount)
        assertEquals(4L, repo.watchersCount)
        assertEquals(12L, repo.openIssuesCount)
        assertEquals("trunk", repo.defaultBranch)
        assertTrue(repo.hasIssues)
        assertTrue(repo.hasDiscussions)
        assertTrue(repo.fork)
        assertFalse(repo.isPrivate)
        assertEquals(Instant.parse("2026-10-19T12:00:00Z"), repo.pushedAt)
        assertEquals("up/up", repo.parent?.fullName)
        assertEquals("MIT", repo.license?.spdxId)
        assertEquals(listOf("android", "kotlin"), repo.topics)
        assertTrue(repo.permissions?.canWrite == true)
    }

    @Test
    fun `minimal search result still has a name with owner`() {
        val repo = json.decodeFromString(
            Repo.serializer(),
            """{"id":3,"name":"solo","owner":{"login":"someone"}}""",
        )
        assertEquals("someone/solo", repo.nameWithOwner)
        assertNull(repo.pushedAt)
        assertFalse(repo.permissions?.canWrite == true)
    }

    @Test
    fun `full name wins over the owner fallback`() {
        val repo = Repo(name = "repo", fullName = "a/repo", owner = User(login = "b"))
        assertEquals("a/repo", repo.nameWithOwner)
    }
}
