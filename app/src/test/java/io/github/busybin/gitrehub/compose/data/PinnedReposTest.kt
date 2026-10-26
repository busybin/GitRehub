package io.github.busybin.gitrehub.compose.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pinned repositories come from GraphQL, where every optional field can also be `null`. */
class PinnedReposTest {

    private fun data(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `pinned items map to cards`() {
        val data = data(
            """
            {"user":{"pinnedItems":{"nodes":[
              {"nameWithOwner":"busybin/gitrehub","description":"A GitHub client",
               "stargazerCount":12,"primaryLanguage":{"name":"Kotlin"}},
              {"nameWithOwner":"busybin/DelugeLauncher","description":null,
               "stargazerCount":0,"primaryLanguage":null}
            ]}}}
            """.trimIndent()
        )
        val repos = PinnedRepos.parse(data)
        assertEquals(2, repos.size)
        assertEquals("busybin/gitrehub", repos[0].nameWithOwner)
        assertEquals("A GitHub client", repos[0].description)
        assertEquals(12, repos[0].stars)
        assertEquals("Kotlin", repos[0].language)
        assertNull(repos[1].description)
        assertNull(repos[1].language)
    }

    @Test
    fun `missing or unusable nodes are skipped`() {
        assertTrue(PinnedRepos.parse(data("""{"user":null}""")).isEmpty())
        assertTrue(PinnedRepos.parse(data("""{"user":{"pinnedItems":{"nodes":null}}}""")).isEmpty())
        val repos = PinnedRepos.parse(
            data("""{"user":{"pinnedItems":{"nodes":[null,{"nameWithOwner":"a/b"}]}}}""")
        )
        assertEquals(listOf("a/b"), repos.map { it.nameWithOwner })
    }
}
