package io.github.busybin.gitrehub.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recursive tree feeds the file finder; directories must not show up as files. */
class GitTreeJsonTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `blobs become files, trees do not`() {
        val tree = json.decodeFromString(
            GitTree.serializer(),
            """
            {
              "sha": "abc",
              "truncated": false,
              "tree": [
                {"path": "src", "mode": "040000", "type": "tree", "sha": "t1"},
                {"path": "src/Main.kt", "mode": "100644", "type": "blob", "sha": "b1", "size": 120},
                {"path": "README.md", "mode": "100644", "type": "blob", "sha": "b2", "size": 40}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(listOf("src/Main.kt", "README.md"), tree.files)
        assertFalse(tree.truncated)
    }

    @Test
    fun `truncated tree is reported`() {
        val tree = json.decodeFromString(
            GitTree.serializer(),
            """{"truncated":true,"tree":[{"path":"a.bin","type":"blob"}]}""",
        )
        assertTrue(tree.truncated)
        assertEquals(listOf("a.bin"), tree.files)
    }
}
