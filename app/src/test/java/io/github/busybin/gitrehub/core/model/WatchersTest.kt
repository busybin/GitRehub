package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Watchers come from GraphQL (REST `/subscribers` is 404 for other people's repositories). */
class WatchersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses a page of watchers`() {
        val body = json.parseToJsonElement(
            """
            {"repository":{"watchers":{
              "pageInfo":{"hasNextPage":true,"endCursor":"Y3Vyc29y"},
              "nodes":[
                {"databaseId":7,"login":"ann","name":"Ann","avatarUrl":"https://x/a.png"},
                {"databaseId":null,"login":"ghost","name":null,"avatarUrl":null}
              ]}}}
            """.trimIndent(),
        )
        val page = parseWatchers(body, json)
        assertEquals(2, page.users.size)
        assertEquals(7L, page.users[0].id)
        assertEquals("ann", page.users[0].login)
        assertEquals("https://x/a.png", page.users[0].avatarUrl)
        // No databaseId -> a stable fallback key (LazyColumn keys must be unique).
        assertEquals("ghost".hashCode().toLong(), page.users[1].id)
        assertTrue(page.hasNext)
        assertEquals("Y3Vyc29y", page.cursor)
    }

    @Test
    fun `last page has no cursor`() {
        val body = json.parseToJsonElement(
            """
            {"repository":{"watchers":{
              "pageInfo":{"hasNextPage":false,"endCursor":null},
              "nodes":[{"databaseId":1,"login":"ann"}]}}}
            """.trimIndent(),
        )
        val page = parseWatchers(body, json)
        assertFalse(page.hasNext)
        assertNull(page.cursor)
    }

    @Test
    fun `a missing repository is an empty page, not a crash`() {
        val page = parseWatchers(json.parseToJsonElement("""{"repository":null}"""), json)
        assertTrue(page.users.isEmpty())
        assertFalse(page.hasNext)
    }
}
