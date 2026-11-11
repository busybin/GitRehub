package io.github.busybin.gitrehub.core.offline

import io.github.busybin.gitrehub.core.model.GitTree
import io.github.busybin.gitrehub.core.model.GitTreeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoPackRulesTest {

    @Test
    fun textFilesAreIncluded() {
        assertTrue(RepoPackRules.isTextPath("README.md", 100))
        assertTrue(RepoPackRules.isTextPath("app/src/main/java/Main.kt", 4_000))
        assertTrue(RepoPackRules.isTextPath("LICENSE", 1_000))
    }

    @Test
    fun binariesAreSkipped() {
        assertFalse(RepoPackRules.isTextPath("icon.png", 100))
        assertFalse(RepoPackRules.isTextPath("lib/arm64/libfoo.so", 100))
        assertFalse(RepoPackRules.isTextPath("release.apk", 100))
        assertFalse(RepoPackRules.isTextPath("assets/font.woff2", 100))
    }

    @Test
    fun hugeAndEmptyFilesAreSkipped() {
        assertFalse(RepoPackRules.isTextPath("big.json", RepoPackRules.LARGE_MAX_BYTES + 1))
        assertFalse(RepoPackRules.isTextPath("empty.txt", 0))
    }

    @Test
    fun gitAndNodeModulesAreSkipped() {
        assertFalse(RepoPackRules.isTextPath(".git/config", 100))
        assertFalse(RepoPackRules.isTextPath("web/node_modules/pkg/index.js", 100))
    }

    @Test
    fun treeFilterKeepsOnlyBlobsAndSumsSizes() {
        val tree = GitTree(
            sha = "root",
            tree = listOf(
                GitTreeEntry(path = "README.md", type = "blob", size = 300, sha = "a"),
                GitTreeEntry(path = "src", type = "tree", sha = "b"),
                GitTreeEntry(path = "logo.png", type = "blob", size = 900, sha = "c"),
                GitTreeEntry(path = "Main.kt", type = "blob", size = 700, sha = "d"),
            ),
        )
        val (files, smallBytes, _) = RepoPackRules.textFiles(tree, includeSmall = true, includeLarge = false)
        assertEquals(listOf("README.md", "Main.kt"), files.map { it.path })
        assertEquals(1_000L, smallBytes)
    }

    @Test
    fun largeFilesAreOnlyPackedWhenAsked() {
        val tree = GitTree(
            tree = listOf(
                GitTreeEntry(path = "small.txt", type = "blob", size = 100, sha = "a"),
                GitTreeEntry(path = "big.txt", type = "blob", size = RepoPackRules.SMALL_MAX_BYTES + 1, sha = "b"),
            ),
        )
        val (withoutLarge, _, largeBytes) = RepoPackRules.textFiles(tree, true, false)
        assertEquals(listOf("small.txt"), withoutLarge.map { it.path })
        assertEquals(RepoPackRules.SMALL_MAX_BYTES + 1, largeBytes)
        val (withLarge, _, _) = RepoPackRules.textFiles(tree, true, true)
        assertEquals(listOf("small.txt", "big.txt"), withLarge.map { it.path })
    }

    @Test
    fun packagePathsCannotEscapeTheirDirectory() {
        assertEquals("a/b.txt", RepoPacks.safePath("/a/b.txt"))
        assertNull(RepoPacks.safePath("../../etc/passwd"))
        assertNull(RepoPacks.safePath("a/../b"))
        assertNull(RepoPacks.safePath(""))
    }

    @Test
    fun digestDependsOnAccountAndRepository() {
        val a = RepoPacks.digest("me", "o", "r")
        val b = RepoPacks.digest("other", "o", "r")
        assertEquals(32, a.length)
        assertTrue(a != b)
    }

    @Test
    fun selectionStopsAtTheQuotaAndCountsTheRestAsSkipped() {
        val candidates = listOf(
            GitTreeEntry(path = "a.txt", type = "blob", size = 10, sha = "a"),
            GitTreeEntry(path = "b.txt", type = "blob", size = 10, sha = "b"),
            GitTreeEntry(path = "c.txt", type = "blob", size = 10, sha = "c"),
        )
        val selection = RepoPackRules.select(candidates, maxBytes = 20)
        assertEquals(listOf("a.txt", "b.txt"), selection.files.map { it.path })
        assertEquals(1, selection.skipped)
    }

    @Test
    fun selectionSkipsAnOversizedFileInsteadOfTruncatingThePack() {
        val candidates = listOf(
            GitTreeEntry(path = "a-huge.json", type = "blob", size = 100, sha = "a"),
            GitTreeEntry(path = "b.txt", type = "blob", size = 10, sha = "b"),
            GitTreeEntry(path = "c.txt", type = "blob", size = 10, sha = "c"),
        )
        val selection = RepoPackRules.select(candidates, maxBytes = 20)
        assertEquals(listOf("b.txt", "c.txt"), selection.files.map { it.path })
        assertEquals(1, selection.skipped)
    }

    @Test
    fun unchangedFilesNeedTheSamePathAndBlobSha() {
        val planned = listOf(
            RepoPackFile(path = "a.txt", size = 1, sha = "same"),
            RepoPackFile(path = "b.txt", size = 1, sha = "moved"),
            RepoPackFile(path = "c.txt", size = 1, sha = "same"),
            RepoPackFile(path = "d.txt", size = 1, sha = ""),
        )
        val old = mapOf("a.txt" to "same", "b.txt" to "old", "c.txt" to "SAME", "d.txt" to "")
        val reused = RepoPackRules.unchanged(planned, old)
        assertEquals(listOf("a.txt", "c.txt"), reused.map { it.path })
    }

    @Test
    fun unchangedFilesIgnoreNewPaths() {
        val planned = listOf(RepoPackFile(path = "new.txt", size = 1, sha = "x"))
        assertTrue(RepoPackRules.unchanged(planned, emptyMap()).isEmpty())
    }

    @Test
    fun selectionKeepsEverythingThatFits() {
        val candidates = listOf(GitTreeEntry(path = "a.txt", type = "blob", size = 10, sha = "a"))
        val selection = RepoPackRules.select(candidates, maxBytes = 10)
        assertEquals(1, selection.files.size)
        assertEquals(0, selection.skipped)
    }
}
