package io.github.busybin.gitrehub.core.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The archive path must produce exactly the files the blob path would, so these tests pin the
 * GitHub root prefix, the binary/oversize filters and the path-traversal guard.
 */
class RepoPackZipTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, size: Long = 0) = RepoPackFile(path, size, "sha-$path")

    private fun archive(entries: List<Pair<String, ByteArray>>): File {
        val file = tmp.newFile("repo.zip")
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, data) ->
                out.putNextEntry(ZipEntry(name))
                out.write(data)
                out.closeEntry()
            }
        }
        return file
    }

    private fun extract(
        archive: File,
        candidates: Map<String, RepoPackFile>,
        maxBytes: Long = RepoPacks.MAX_REPO_BYTES,
    ): Pair<RepoPackZip.Result, Map<String, String>> {
        val written = linkedMapOf<String, String>()
        val result = RepoPackZip.extract(
            archive = archive,
            candidates = candidates,
            maxBytes = maxBytes,
            write = { path, data -> written[path] = String(data) },
        )
        return result to written
    }

    @Test
    fun stripsTheGithubRootFolderAndSkipsUnplannedFiles() {
        val zip = archive(
            listOf(
                "owner-repo-abc/README.md" to "hello".toByteArray(),
                "owner-repo-abc/src/Main.kt" to "fun main() {}".toByteArray(),
                "owner-repo-abc/logo.png" to ByteArray(32),
            ),
        )
        val (result, written) = extract(
            zip,
            mapOf("README.md" to file("README.md"), "src/Main.kt" to file("src/Main.kt")),
        )
        assertEquals(setOf("README.md", "src/Main.kt"), written.keys)
        assertEquals("hello", written["README.md"])
        assertEquals(2, result.files.size)
        assertEquals(0, result.skipped)
        assertEquals(5L + 13L, result.bytes)
    }

    @Test
    fun acceptsAnArchiveWithoutTheRootFolder() {
        val zip = archive(listOf("README.md" to "hi".toByteArray()))
        val (result, written) = extract(zip, mapOf("README.md" to file("README.md")))
        assertEquals("hi", written["README.md"])
        assertEquals(0, result.skipped)
    }

    @Test
    fun rejectsEntriesThatEscapeThePackDirectory() {
        val zip = archive(
            listOf(
                "owner-repo-abc/../../evil.txt" to "boom".toByteArray(),
                "owner-repo-abc/README.md" to "ok".toByteArray(),
            ),
        )
        val escaped = File(tmp.root, "../evil.txt")
        val (result, written) = extract(zip, mapOf("README.md" to file("README.md")))
        assertFalse(escaped.exists())
        assertEquals(setOf("README.md"), written.keys)
        assertEquals(0, result.skipped)
    }

    @Test
    fun keepsTheFirstDuplicateAndCountsMissingFiles() {
        val zip = archive(
            listOf(
                "root/README.md" to "first".toByteArray(),
                "README.md" to "second".toByteArray(),
            ),
        )
        val (result, written) = extract(
            zip,
            mapOf("README.md" to file("README.md"), "missing.txt" to file("missing.txt")),
        )
        assertEquals("first", written["README.md"])
        assertEquals(1, result.skipped)
    }

    @Test
    fun stopsWritingAtTheQuota() {
        val zip = archive(
            listOf(
                "root/a.txt" to "12345".toByteArray(),
                "root/b.txt" to "12345".toByteArray(),
            ),
        )
        val (result, written) = extract(
            zip,
            mapOf("a.txt" to file("a.txt"), "b.txt" to file("b.txt")),
            maxBytes = 5,
        )
        assertEquals(setOf("a.txt"), written.keys)
        assertEquals(5L, result.bytes)
        assertEquals(1, result.skipped)
    }
}
