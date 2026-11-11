package io.github.busybin.gitrehub.compose.common

import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A repository the user opened, with the moment it was opened. */
@Serializable
data class RecentRepo(val name: String, val at: Long)

/**
 * The most recently opened repositories, newest first.
 *
 * Local only: GitHub has no "recently viewed" and the list has to survive restarts, so it is a small
 * JSON array in prefs rather than a request. Names are enough — the list screen fetches each one, so a
 * repository deleted or made private simply drops out of the result.
 */
object RecentRepos {

    private const val KEY = "m3_recent_repos"
    private const val LIMIT = 30
    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<RecentRepo>> = _items.asStateFlow()

    fun record(fullName: String, at: Long = System.currentTimeMillis()) {
        val clean = fullName.trim()
        if (clean.isEmpty()) return
        val next = listOf(RecentRepo(clean, at)) + _items.value.filterNot { it.name == clean }
        write(next.take(LIMIT))
    }

    fun clear() = write(emptyList())

    private fun read(): List<RecentRepo> = runCatching {
        PrefHelper.getString(KEY)
            ?.takeIf { it.isNotBlank() }
            ?.let { json.decodeFromString<List<RecentRepo>>(it) }
            ?: emptyList()
    }.getOrDefault(emptyList())

    private fun write(items: List<RecentRepo>) {
        runCatching { PrefHelper.putAny(KEY, json.encodeToString(items)) }
        _items.value = items
    }
}
