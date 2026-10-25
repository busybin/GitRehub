package io.github.busybin.gitrehub.compose.common

import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A search the user pinned: the query plus the tab it was run on (`SearchScope.name`). */
data class SavedSearch(val query: String, val scope: String)

/**
 * Saved searches and favourite repositories.
 *
 * Both are local-only (no API exists for either): a search is just a query string, and a favourite
 * is a `owner/repo` name fetched on demand, so it works for repositories the list never loaded.
 */
object SavedPrefs {

    private const val KEY_SEARCHES = "m3_saved_searches"
    private const val KEY_FAVORITES = "m3_favorite_repos"
    private const val MAX_SEARCHES = 20

    private val _searches = MutableStateFlow(readSearches())
    val searches: StateFlow<List<SavedSearch>> = _searches.asStateFlow()

    private val _favorites = MutableStateFlow(PrefHelper.getStringSet(KEY_FAVORITES))
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun saveSearch(query: String, scope: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        val next = listOf(SavedSearch(clean, scope)) +
            _searches.value.filterNot { it.query == clean && it.scope == scope }
        writeSearches(next.take(MAX_SEARCHES))
    }

    fun isSaved(query: String, scope: String): Boolean =
        _searches.value.any { it.query == query.trim() && it.scope == scope }

    fun removeSearch(search: SavedSearch) {
        writeSearches(_searches.value.filterNot { it == search })
    }

    fun toggleFavorite(fullName: String) {
        val clean = fullName.trim()
        if (clean.isEmpty()) return
        val next = _favorites.value.toMutableSet()
        if (!next.add(clean)) next.remove(clean)
        PrefHelper.putStringSet(KEY_FAVORITES, next)
        _favorites.value = next
    }

    fun isFavorite(fullName: String): Boolean = fullName in _favorites.value

    private fun writeSearches(value: List<SavedSearch>) {
        PrefHelper.putAny(
            KEY_SEARCHES,
            value.joinToString("\n") { "${it.scope}\t${it.query.replace('\n', ' ')}" },
        )
        _searches.value = value
    }

    private fun readSearches(): List<SavedSearch> {
        val raw = PrefHelper.getString(KEY_SEARCHES) ?: return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val at = line.indexOf('\t')
            if (at <= 0) null else SavedSearch(line.substring(at + 1), line.take(at))
        }.toList()
    }
}
