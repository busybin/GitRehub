package io.github.busybin.gitrehub.compose.common

import io.github.busybin.gitrehub.core.net.ApiException
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** A comment that could not be sent because the network failed; kept until it goes through. */
@Serializable
data class DraftComment(
    val id: String = "",
    val owner: String = "",
    val repo: String = "",
    val number: Int = 0,
    val body: String = "",
    val createdAt: Long = 0,
)

/**
 * Offline queue for comments: a network failure keeps the text locally instead of losing it, and
 * the drafts screen (or the next app launch) retries it.
 *
 * A draft is dropped only when the answer proves it can never be created (the issue is gone or
 * the body is invalid). Everything else, including 5xx from a proxy, keeps the text so a long
 * comment is never lost; the user can check GitHub before deleting a draft.
 */
object DraftQueue {

    private const val KEY = "m3_comment_drafts"
    private val json = Json { ignoreUnknownKeys = true }

    private val _drafts = MutableStateFlow(read())
    val drafts: StateFlow<List<DraftComment>> = _drafts.asStateFlow()

    fun add(owner: String, repo: String, number: Int, body: String): DraftComment {
        val draft = DraftComment(
            id = UUID.randomUUID().toString(),
            owner = owner,
            repo = repo,
            number = number,
            body = body,
            createdAt = System.currentTimeMillis(),
        )
        write(_drafts.value + draft)
        return draft
    }

    fun remove(id: String) = write(_drafts.value.filterNot { it.id == id })

    /** Sends one draft; returns true when it is gone from the queue (sent, or rejected by GitHub). */
    suspend fun send(draft: DraftComment): Boolean = withContext(Dispatchers.IO) {
        val result = runCatching {
            GitHubApi.createComment(draft.owner, draft.repo, draft.number, draft.body)
        }
        val status = (result.exceptionOrNull() as? ApiException)?.error?.status ?: 0
        // Only a definitive rejection (the issue is gone or the body is invalid) makes the draft
        // useless. Network errors, auth failures, rate limits and 5xx keep it: a gateway timeout
        // may not have reached GitHub at all, and dropping the text would lose the user's work.
        val finalRejection = status == 404 || status == 410 || status == 422
        if (result.isSuccess || finalRejection) {
            remove(draft.id)
            true
        } else {
            false
        }
    }

    /** Retries everything; returns how many drafts were delivered. */
    suspend fun flushAll(): Int {
        var sent = 0
        _drafts.value.toList().forEach { draft ->
            if (send(draft)) sent++
        }
        return sent
    }

    private fun write(value: List<DraftComment>) {
        PrefHelper.putAny(
            KEY,
            json.encodeToString(ListSerializer(DraftComment.serializer()), value),
        )
        _drafts.value = value
    }

    private fun read(): List<DraftComment> {
        val raw = PrefHelper.getString(KEY) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(DraftComment.serializer()), raw)
        }.getOrDefault(emptyList())
    }
}
