package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** `Gist` decodes straight from the gists API; a renamed field silently empties the screen. */
class GistJsonTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val body = """
        {
          "id": "aa5a315d61ae9438b18d",
          "description": "Example gist",
          "public": false,
          "comments": 2,
          "created_at": "2026-10-01T10:00:00Z",
          "updated_at": "2026-10-02T11:30:00Z",
          "html_url": "https://gist.github.com/aa5a315d61ae9438b18d",
          "owner": {"id": 7, "login": "octocat", "avatar_url": "https://x/o.png"},
          "files": {
            "z.txt": {"filename": "z.txt", "type": "text/plain", "language": null,
                      "raw_url": "https://gist.githubusercontent.com/u/1/raw/z.txt",
                      "size": 12, "content": "hello", "truncated": true},
            "a.kt": {"filename": "a.kt", "type": "text/plain", "language": "Kotlin",
                      "raw_url": "https://gist.githubusercontent.com/u/1/raw/a.kt",
                      "size": 20, "content": "fun main() {}"}
          }
        }
    """.trimIndent()

    @Test
    fun `decodes the fields the screens read`() {
        val gist = json.decodeFromString(Gist.serializer(), body)
        assertEquals("aa5a315d61ae9438b18d", gist.id)
        assertEquals("Example gist", gist.title)
        assertFalse(gist.public)
        assertEquals(2, gist.comments)
        assertEquals("octocat", gist.owner?.login)
        assertEquals(Instant.parse("2026-10-02T11:30:00Z"), gist.updatedAt)
        assertEquals("https://gist.github.com/aa5a315d61ae9438b18d", gist.htmlUrl)
        assertEquals(2, gist.files.size)
        assertEquals(listOf("a.kt", "z.txt"), gist.filesSorted.map { it.filename })
        assertEquals("Kotlin", gist.files["a.kt"]?.language)
        assertTrue(gist.files["z.txt"]?.truncated == true)
        assertFalse(gist.files.getValue("a.kt").truncated)
    }

    @Test
    fun `title falls back to the first file, then to the id`() {
        val unnamed = json.decodeFromString(
            Gist.serializer(),
            """{"id":"abc","files":{"only.md":{"filename":"only.md","content":"x"}}}""",
        )
        assertEquals("only.md", unnamed.title)

        val empty = json.decodeFromString(Gist.serializer(), """{"id":"abc"}""")
        assertEquals("abc", empty.title)
        assertNull(empty.updatedAt)
    }
}
