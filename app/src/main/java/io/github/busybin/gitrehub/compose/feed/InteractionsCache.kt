package io.github.busybin.gitrehub.compose.feed

import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Disk copy of the "who starred / forked / opened something on my repos" snapshot
 * ([io.github.busybin.gitrehub.core.net.GitHubApi.repoInteractions]).
 *
 * That source is a GraphQL POST, which the OkHttp disk cache never stores. Without this copy the
 * feed painted its cached event streams first and the star/fork rows only appeared once the network
 * refresh finished. Stored per login, replaced on every successful fetch.
 */
object InteractionsCache {

    private const val KEY_PREFIX = "m3_interactions_"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun cacheKey(login: String) = KEY_PREFIX + login.lowercase()

    /** Never throws: an unreadable value is treated as "no cache". */
    fun load(login: String?): List<Event> {
        if (login.isNullOrBlank()) return emptyList()
        return decode(runCatching { PrefHelper.getString(cacheKey(login)) }.getOrNull())
    }

    /** An empty answer clears the old snapshot instead of leaving stale star/fork rows behind. */
    fun save(login: String, events: List<Event>) {
        if (login.isBlank()) return
        runCatching {
            if (events.isEmpty()) PrefHelper.clearKey<String>(cacheKey(login))
            else PrefHelper.putAny(cacheKey(login), encode(events))
        }
    }

    fun encode(events: List<Event>): String =
        json.encodeToString(ListSerializer(Event.serializer()), events)

    fun decode(raw: String?): List<Event> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Event.serializer()), raw)
        }.getOrDefault(emptyList())
    }
}
