package io.github.busybin.gitrehub.core.offline

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Streaming extractor for a repository zipball.
 *
 * A zipball is one HTTP request for the whole tree, which is roughly two orders of magnitude
 * cheaper than one `git/blobs` call per file. Only the paths the pack plan selected are written;
 * binaries, excluded large files and anything outside the pack quota are skipped while reading.
 *
 * Kept free of Android APIs so the path rules can be unit-tested with a generated archive.
 */
object RepoPackZip {

    data class Result(
        val files: List<RepoPackFile> = emptyList(),
        val bytes: Long = 0L,
        /** Selected paths that the archive did not contain, or that did not fit the quota. */
        val skipped: Int = 0,
    )

    /**
     * Extracts [candidates] (path → planned file) from [archive] through [write].
     *
     * GitHub prefixes every entry with `owner-repo-sha/`; both the prefixed and the raw path are
     * tried, so a hand-made archive without the prefix works too. Duplicate entries keep the first
     * copy. [maxBytes] stops the extraction when the pack budget is exhausted.
     */
    fun extract(
        archive: File,
        candidates: Map<String, RepoPackFile>,
        maxBytes: Long,
        write: (path: String, bytes: ByteArray) -> Unit,
        onProgress: (done: Int, total: Int, bytes: Long) -> Unit = { _, _, _ -> },
        shouldContinue: () -> Boolean = { true },
    ): Result {
        if (candidates.isEmpty()) return Result()
        val total = candidates.size
        val written = LinkedHashMap<String, RepoPackFile>(total)
        var bytes = 0L
        var processed = 0

        ZipInputStream(BufferedInputStream(FileInputStream(archive), 64 * 1024)).use { zip ->
            while (true) {
                if (!shouldContinue()) throw CancellationException("Pack extraction cancelled")
                val entry = zip.nextEntry ?: break
                try {
                    if (entry.isDirectory) continue
                    val path = match(entry.name, candidates) ?: continue
                    if (written.containsKey(path)) continue
                    processed += 1
                    val planned = candidates.getValue(path)
                    val data = readAtMost(zip, RepoPackRules.LARGE_MAX_BYTES, shouldContinue)
                    if (data != null && data.isNotEmpty() && bytes + data.size <= maxBytes) {
                        write(path, data)
                        bytes += data.size
                        written[path] = planned.copy(size = data.size.toLong())
                    }
                    onProgress(processed, total, bytes)
                } finally {
                    runCatching { zip.closeEntry() }
                }
            }
        }
        return Result(
            files = written.values.toList(),
            bytes = bytes,
            skipped = total - written.size,
        )
    }

    /**
     * Re-roots an entry name onto a pack path. Returns the prefixed path when the archive kept
     * GitHub's root folder, otherwise the name itself. Anything that escapes the pack directory
     * (`..`, absolute paths) is rejected by [RepoPacks.safePath].
     */
    private fun match(raw: String, candidates: Map<String, RepoPackFile>): String? {
        val clean = raw.replace('\\', '/').trim('/')
        if (clean.isBlank()) return null
        val direct = RepoPacks.safePath(clean)
        if (direct != null && candidates.containsKey(direct)) return direct
        val slash = clean.indexOf('/')
        if (slash <= 0) return null
        val stripped = RepoPacks.safePath(clean.substring(slash + 1))
        return stripped?.takeIf { candidates.containsKey(it) }
    }

    /** Reads one entry, refusing anything above [limit]; `null` means "too large". */
    private fun readAtMost(
        input: InputStream,
        limit: Long,
        shouldContinue: () -> Boolean,
    ): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            if (!shouldContinue()) throw CancellationException("Pack extraction cancelled")
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
