package io.github.busybin.gitrehub.compose.common

import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One canned reply, e.g. "please attach the app version and a logcat". */
@Serializable
data class SavedReply(val title: String, val body: String)

/**
 * Canned replies for maintainers, inserted into any comment composer. Local-only: GitHub has no
 * snippet storage in the API, and the point is one tap on a phone keyboard.
 */
object SavedReplies {

    private const val KEY = "m3_saved_replies"
    private const val MAX = 30
    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<SavedReply>> = _items.asStateFlow()

    fun add(body: String) {
        val text = body.trim()
        if (text.isEmpty()) return
        val title = text.lineSequence().firstOrNull().orEmpty().take(60)
        val next = listOf(SavedReply(title, text)) + _items.value.filterNot { it.body == text }
        write(next.take(MAX))
    }

    fun remove(reply: SavedReply) {
        write(_items.value.filterNot { it == reply })
    }

    /** Replace the whole list (settings import). */
    fun replaceAll(replies: List<SavedReply>) {
        write(replies.take(MAX))
    }

    private fun write(value: List<SavedReply>) {
        runCatching { PrefHelper.putAny(KEY, json.encodeToString(value)) }
        _items.value = value
    }

    private fun read(): List<SavedReply> {
        val raw = PrefHelper.getString(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SavedReply>>(raw) }.getOrDefault(emptyList())
    }
}
