package io.github.busybin.gitrehub.compose.issues

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The auto-merge mutation sends `MergeMethod.name` as a GraphQL enum, which only accepts
 * `MERGE` / `SQUASH` / `REBASE`. Renaming an enum entry would break merging at runtime, so the
 * expected wire values are pinned here.
 */
class MergeMethodTest {

    @Test
    fun graphQlEnumNamesMatchGithubsValues() {
        assertEquals(
            listOf("MERGE", "SQUASH", "REBASE"),
            MergeMethod.entries.map { it.name },
        )
    }

    @Test
    fun restAndGraphQlValuesAgree() {
        MergeMethod.entries.forEach { method ->
            assertEquals(method.name.lowercase(), method.api)
        }
    }
}
