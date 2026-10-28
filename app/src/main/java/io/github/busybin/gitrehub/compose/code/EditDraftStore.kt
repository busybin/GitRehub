package io.github.busybin.gitrehub.compose.code

import io.github.busybin.gitrehub.App
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** An unsubmitted edit: the blob it started from plus the text, so a stale draft cannot overwrite. */
@Serializable
data class EditDraft(val sha: String? = null, val text: String = "")

/**
 * On-disk drafts for the file editor. Saved state is capped by the Binder transaction size, so a
 * long edit cannot ride along with it; keeping a copy in the cache directory is what makes a large
 * edit survive process death or a configuration change.
 */
object EditDraftStore {

    private const val MAX_BYTES = 4L * 1024 * 1024
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val SWEEP_INTERVAL_MS = 60L * 60 * 1000
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var lastSweep = 0L

    private fun dir(): File = File(App.getInstance().cacheDir, "edit-drafts").apply { mkdirs() }

    private fun file(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return File(dir(), digest.joinToString("") { "%02x".format(it) } + ".txt")
    }

    fun read(key: String): EditDraft? = runCatching {
        sweep()
        val draft = file(key)
        when {
            !draft.isFile -> null
            draft.length() > MAX_BYTES -> null
            System.currentTimeMillis() - draft.lastModified() > MAX_AGE_MS -> {
                draft.delete()
                null
            }
            else -> json.decodeFromString<EditDraft>(draft.readText())
        }
    }.getOrNull()

    fun write(key: String, sha: String?, text: String) {
        runCatching {
            val payload = json.encodeToString(EditDraft(sha, text))
            if (payload.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return
            file(key).writeText(payload)
        }
    }

    fun clear(key: String) {
        runCatching { file(key).delete() }
    }

    /** Drafts of abandoned edits are never read again, so drop them once an hour. */
    private fun sweep() {
        val now = System.currentTimeMillis()
        if (now - lastSweep < SWEEP_INTERVAL_MS) return
        lastSweep = now
        dir().listFiles()?.forEach { draft ->
            if (now - draft.lastModified() > MAX_AGE_MS) draft.delete()
        }
    }
}
