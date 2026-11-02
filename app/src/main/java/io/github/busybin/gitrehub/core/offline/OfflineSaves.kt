package io.github.busybin.gitrehub.core.offline

import android.content.Context
import io.github.busybin.gitrehub.App
import io.github.busybin.gitrehub.core.model.Comment
import io.github.busybin.gitrehub.core.model.Issue
import io.github.busybin.gitrehub.core.model.PullRequest
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Threads the user saved for offline reading.
 *
 * Plain JSON files under `filesDir/offline/` plus a small index in MMKV: an issue with a hundred
 * comments is a few hundred kilobytes, which does not belong in the preferences store.
 *
 * Saved copies are private data (a private issue is still private after logout), so everything is
 * partitioned per account and wiped when the account changes.
 */
@Serializable
data class SavedThreadMeta(
    val account: String = "",
    val owner: String = "",
    val repo: String = "",
    val number: Int = 0,
    val title: String = "",
    val isPull: Boolean = false,
    val updatedAt: Long = 0L,
    val savedAt: Long = 0L,
    val comments: Int = 0,
) {
    val key: String get() = "$owner/$repo#$number"
    val repoFullName: String get() = "$owner/$repo"
}

@Serializable
data class SavedThread(
    val issue: Issue,
    val pullRequest: PullRequest? = null,
    val comments: List<Comment> = emptyList(),
    val savedAt: Long = 0L,
)

object OfflineSaves {

    private const val INDEX_KEY = "m3_offline_saved"

    /** Hard cap: 200 threads is already far more than anyone reads on a flight. */
    const val MAX_ITEMS = 200

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun dir(context: Context): File =
        File(context.filesDir, "offline").apply { mkdirs() }

    private fun file(context: Context, meta: SavedThreadMeta): File = File(
        dir(context),
        "${sanitize(meta.account)}_${sanitize(meta.owner)}_${sanitize(meta.repo)}_${meta.number}.json",
    )

    private fun sanitize(value: String): String =
        value.map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == '.') it else '_' }
            .joinToString("")

    /** Every saved thread of [account], newest first. */
    fun index(account: String): List<SavedThreadMeta> = readIndex()
        .filter { it.account == account }
        .sortedByDescending { it.savedAt }

    fun save(
        context: Context,
        account: String,
        issue: Issue,
        pullRequest: PullRequest?,
        comments: List<Comment>,
    ): SavedThreadMeta {
        val now = System.currentTimeMillis()
        val full = issue.repository?.nameWithOwner ?: issue.repoFullName.orEmpty()
        val meta = SavedThreadMeta(
            account = account,
            owner = full.substringBefore("/").ifBlank { "unknown" },
            repo = full.substringAfter("/", "").ifBlank { "unknown" },
            number = issue.number,
            title = issue.title.orEmpty(),
            isPull = issue.isPullRequest,
            updatedAt = issue.updatedAt?.toEpochMilli() ?: 0L,
            savedAt = now,
            comments = comments.size,
        )
        file(context, meta).writeText(
            json.encodeToString(SavedThread.serializer(), SavedThread(issue, pullRequest, comments, now)),
        )
        val next = (readIndex().filterNot { it.account == meta.account && it.key == meta.key } + meta)
            .sortedByDescending { it.savedAt }
        writeIndex(next, context)
        return meta
    }

    fun load(context: Context, meta: SavedThreadMeta): SavedThread? = runCatching {
        val text = file(context, meta).readText()
        json.decodeFromString(SavedThread.serializer(), text)
    }.getOrNull()

    fun has(account: String, owner: String, repo: String, number: Int): Boolean =
        readIndex().any { it.account == account && it.owner == owner && it.repo == repo && it.number == number }

    fun remove(context: Context, meta: SavedThreadMeta) {
        runCatching { file(context, meta).delete() }
        writeIndex(readIndex().filterNot { it.account == meta.account && it.key == meta.key }, context)
    }

    /** Drop every copy of [account]; called when the account changes or signs out. */
    fun clearAccount(account: String) {
        val context = App.getInstance()
        readIndex().filter { it.account == account }.forEach { meta ->
            runCatching { file(context, meta).delete() }
        }
        writeIndex(readIndex().filterNot { it.account == account }, context)
    }

    /** Drop everything: signing out of the last account leaves no readable copy behind. */
    fun clearAll() {
        val context = App.getInstance()
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
        writeIndex(emptyList(), context)
    }

    private fun writeIndex(items: List<SavedThreadMeta>, context: Context) {
        val trimmed = items.sortedByDescending { it.savedAt }.take(MAX_ITEMS)
        items.filter { it !in trimmed }.forEach { runCatching { file(context, it).delete() } }
        runCatching {
            PrefHelper.putAny(INDEX_KEY, json.encodeToString(trimmed))
        }
    }

    private fun readIndex(): List<SavedThreadMeta> = runCatching {
        val raw = PrefHelper.getString(INDEX_KEY) ?: return emptyList()
        json.decodeFromString<List<SavedThreadMeta>>(raw)
    }.getOrDefault(emptyList())
}
