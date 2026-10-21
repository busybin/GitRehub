package io.github.busybin.gitrehub.compose.issues

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Line numbering of a unified diff: inline review comments are anchored on a **file line** plus a
 * side, so a wrong number puts the comment on the wrong line (or gets a 422 from GitHub).
 */
class PullDiffTest {

    private val patch = """
        @@ -10,6 +10,7 @@ class Foo {
         context one
        -removed line
        +added line
        +second added
         context two
        @@ -30,3 +31,2 @@
        -gone
         tail
    """.trimIndent()

    @Test
    fun addedAndContextLinesUseTheNewFileNumbers() {
        val lines = parsePatch(patch)
        val added = lines.filter { it.kind == DiffLine.Kind.ADDED }
        val removed = lines.filter { it.kind == DiffLine.Kind.REMOVED }

        assertEquals(listOf(11, 12), added.map { it.newLine })
        assertTrue("added lines have no old number", added.all { it.oldLine == null })
        assertEquals("RIGHT", added[0].commentSide)
        assertEquals(11, added[0].commentLine)

        assertEquals(listOf(11, 30), removed.map { it.oldLine })
        assertTrue("removed lines have no new number", removed.all { it.newLine == null })
        assertEquals("LEFT", removed[0].commentSide)
        assertEquals(11, removed[0].commentLine)
    }

    @Test
    fun contextLinesCarryBothNumbersAndTheSecondHunkRestarts() {
        val lines = parsePatch(patch)
        val context = lines.filter { it.kind == DiffLine.Kind.CONTEXT }
        assertEquals(listOf(10 to 10, 12 to 13, 31 to 31), context.map { it.oldLine to it.newLine })
        assertEquals(31, context.last().commentLine)
    }

    @Test
    fun hunkHeadersAreNotCommentable() {
        val lines = parsePatch(patch)
        val hunks = lines.filter { it.kind == DiffLine.Kind.HUNK }
        assertEquals(2, hunks.size)
        assertTrue(hunks.all { it.commentLine == null && it.commentSide == null })
    }

    @Test
    fun blankAndMissingPatchesYieldNothing() {
        assertEquals(emptyList<DiffLine>(), parsePatch(null))
        assertEquals(emptyList<DiffLine>(), parsePatch(""))
    }

    @Test
    fun noNewlineMarkerDoesNotAdvanceTheCounter() {
        val lines = parsePatch("@@ -1,1 +1,1 @@\n-old\n\\ No newline at end of file\n+new\n")
        val added = lines.first { it.kind == DiffLine.Kind.ADDED }
        assertEquals(1, added.newLine)
        assertTrue(lines.any { it.kind == DiffLine.Kind.NOTE && it.text.startsWith("\\") })
    }
}
