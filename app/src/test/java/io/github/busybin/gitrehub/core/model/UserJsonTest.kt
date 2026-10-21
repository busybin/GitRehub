package io.github.busybin.gitrehub.core.model

import io.github.busybin.gitrehub.core.net.GitHubClient
import org.junit.Assert.assertEquals
import org.junit.Test

class UserJsonTest {

    @Test
    fun `decodes the fields the profile needs`() {
        val json = """
            {
              "id": 65460344,
              "login": "busybin",
              "name": "Bin",
              "avatar_url": "https://avatars.githubusercontent.com/u/65460344",
              "html_url": "https://github.com/busybin",
              "bio": null,
              "company": "none",
              "blog": "",
              "location": "somewhere",
              "email": null,
              "public_repos": 12,
              "public_gists": 3,
              "followers": 7,
              "following": 5,
              "unknown_field": {"nested": true}
            }
        """.trimIndent()

        val user = GitHubClient.json.decodeFromString(User.serializer(), json)

        assertEquals(65460344L, user.id)
        assertEquals("busybin", user.login)
        assertEquals("Bin", user.name)
        assertEquals("https://avatars.githubusercontent.com/u/65460344", user.avatarUrl)
        assertEquals("https://github.com/busybin", user.htmlUrl)
        assertEquals(12L, user.publicRepos)
        assertEquals(3L, user.publicGists)
        assertEquals(7L, user.followers)
        assertEquals(5L, user.following)
        assertEquals(null, user.bio)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val user = GitHubClient.json.decodeFromString(User.serializer(), """{"login":"x"}""")
        assertEquals("x", user.login)
        assertEquals(0L, user.id)
        assertEquals(null, user.name)
    }
}
