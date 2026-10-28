package io.github.busybin.gitrehub.compose.code

import io.github.busybin.gitrehub.App
import java.io.File
import java.security.MessageDigest

/**
 * On-disk drafts for the file editor. Saved state is capped by the Binder transaction size, so a
 * long edit cannot ride along with it; keeping a copy in the cache directory is what makes a large
 * edit survive process death or a configuration change.
 */
object EditDraftStore {

    private const val MAX_BYTES = 4L * 1024 * 1024
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private fun dir(): File = File(App.getInstance().cacheDir, "edit-drafts").apply { mkdirs() }

    private fun file(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return File(dir(), digest.joinToString("") { "%02x".format(it) } + ".txt")
    }

    fun read(key: String): String? = runCatching {
        val draft = file(key)
        when {
            !draft.isFile -> null
            draft.length() > MAX_BYTES -> null
            System.currentTimeMillis() - draft.lastModified() > MAX_AGE_MS -> {
                draft.delete()
                null
            }
            else -> draft.readText()
        }
    }.getOrNull()

    fun write(key: String, text: String) {
        runCatching {
            if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return
            file(key).writeText(text)
        }
    }

    fun clear(key: String) {
        runCatching { file(key).delete() }
    }
}
