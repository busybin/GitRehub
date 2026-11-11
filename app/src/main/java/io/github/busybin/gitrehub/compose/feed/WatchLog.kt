package io.github.busybin.gitrehub.compose.feed

import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.EventActor
import io.github.busybin.gitrehub.core.model.EventPayload
import io.github.busybin.gitrehub.core.model.EventRepo
import io.github.busybin.gitrehub.core.model.EventType
import io.github.busybin.gitrehub.core.model.RepoWatchers
import io.github.busybin.gitrehub.core.model.syntheticId
import io.github.busybin.gitrehub.helper.PrefHelper
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * "Someone started watching my repo" rows for the activity feed.
 *
 * GitHub has no event, no timestamp and no API for subscriptions: the only observable is the
 * repository's current watcher list. This object keeps the logins seen on the previous refresh and
 * turns any login that appears afterwards into a synthetic row. The first refresh backfills the
 * watchers that were already there so the list is not empty; their time is the moment the app first
 * saw them, not the real subscription time (which GitHub does not expose). Watchers that arrive
 * later get the time of the refresh that noticed them.
 *
 * The list is capped at the first 100 watchers per repository by the GraphQL query, so a busy
 * repository can miss one that never enters that window.
 */
object WatchLog {

    private const val KEY_PREFIX = "m3_watch_seen_"

    /** Enough for 15 repos x 100 watchers plus years of churn; keeps the MMKV value small. */
    private const val MAX_RECORDS = 2_000

    /** More rows than this would drown the feed; the oldest are dropped first. */
    private const val MAX_ROWS = 200

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun cacheKey(login: String) = KEY_PREFIX + login.lowercase()

    /** Rows for watchers this install has actually observed arriving. */
    fun known(login: String?): List<Event> {
        if (login.isNullOrBlank()) return emptyList()
        return events(load(login).records)
    }

    /**
     * Folds a fresh snapshot into the stored state and returns every watcher row seen so far.
     * The first call for an account only seeds; it never produces rows.
     */
    fun refresh(
        login: String,
        snapshots: List<RepoWatchers>,
        now: Long = System.currentTimeMillis(),
    ): List<Event> {
        if (login.isBlank()) return emptyList()
        val state = load(login)
        val updated = fold(state, snapshots, login, now)
        if (updated != state) save(login, updated)
        return events(updated.records)
    }

    /** Pure half: no storage, so it is unit-testable. */
    internal fun fold(
        state: WatchState,
        snapshots: List<RepoWatchers>,
        me: String,
        now: Long,
    ): WatchState {
        // No repositories in the answer is not a snapshot of "nobody watches me": seeding from it
        // would make every real watcher look new on the next refresh.
        if (snapshots.isEmpty()) return state
        val records = state.records.associateBy { it.key }.toMutableMap()
        // Upgrading from the "seed only" version: watchers stored without a detection time become
        // rows now, so the list is not empty on the first refresh after the update.
        if (state.seeded && !state.backfilled) {
            records.replaceAll { _, record ->
                if (record.firstSeen == 0L) record.copy(firstSeen = now) else record
            }
        }
        if (!state.seeded) {
            snapshots.forEach { snapshot ->
                snapshot.watchers.forEach { watcher ->
                    if (watcher.login.isBlank() || watcher.login.equals(me, ignoreCase = true)) {
                        return@forEach
                    }
                    records[WatchRecord(snapshot.repo, watcher.login).key] =
                        WatchRecord(snapshot.repo, watcher.login, watcher.avatarUrl, firstSeen = now)
                }
            }
            return WatchState(
                seeded = true,
                backfilled = true,
                records = records.values.sortedBy { it.key },
            )
        }
        snapshots.forEach { snapshot ->
            snapshot.watchers.forEach { watcher ->
                if (watcher.login.isBlank() || watcher.login.equals(me, ignoreCase = true)) {
                    return@forEach
                }
                val record = WatchRecord(snapshot.repo, watcher.login, watcher.avatarUrl)
                if (record.key !in records) {
                    records[record.key] = record.copy(firstSeen = now)
                }
            }
        }
        return WatchState(
            seeded = true,
            backfilled = true,
            records = records.values
                .sortedByDescending { it.firstSeen }
                .take(MAX_RECORDS),
        )
    }

    /** Every record with a detection time becomes a row, newest first. */
    internal fun events(records: List<WatchRecord>): List<Event> = records
        .asSequence()
        .filter { it.firstSeen > 0L }
        .sortedWith(compareByDescending<WatchRecord> { it.firstSeen }.thenBy { it.login })
        .take(MAX_ROWS)
        .map { record ->
            Event(
                id = syntheticId(EventType.WatchStarted.name, record.repo, record.login, record.firstSeen.toString()),
                type = EventType.WatchStarted,
                createdAt = Instant.ofEpochMilli(record.firstSeen),
                actor = EventActor(login = record.login, avatarUrl = record.avatarUrl),
                repo = EventRepo(
                    name = record.repo.substringAfter('/'),
                    fullName = record.repo,
                ),
                payload = EventPayload(action = "started"),
            )
        }
        .toList()

    /** Never throws: an unreadable value is treated as "never seen this account". */
    private fun load(login: String): WatchState {
        val raw = runCatching { PrefHelper.getString(cacheKey(login)) }.getOrNull()
        if (raw.isNullOrBlank()) return WatchState()
        return runCatching { json.decodeFromString(WatchState.serializer(), raw) }
            .getOrDefault(WatchState())
    }

    private fun save(login: String, state: WatchState) {
        runCatching {
            PrefHelper.putAny(cacheKey(login), json.encodeToString(WatchState.serializer(), state))
        }
    }
}

/** One login seen watching one repository; [firstSeen] == 0 only survives in state from older builds. */
@Serializable
internal data class WatchRecord(
    val repo: String,
    val login: String,
    val avatarUrl: String? = null,
    val firstSeen: Long = 0L,
) {
    /** GitHub treats both owner/repo and logins case-insensitively (and logins can be re-cased). */
    val key: String get() = "${repo.lowercase()}|${login.lowercase()}"
}

@Serializable
internal data class WatchState(
    val seeded: Boolean = false,
    /**
     * False on state written by the old "seed only" build: its records still carry `firstSeen = 0`
     * and are stamped with the next refresh time, so upgrading does not leave the list empty.
     */
    val backfilled: Boolean = false,
    val records: List<WatchRecord> = emptyList(),
)
