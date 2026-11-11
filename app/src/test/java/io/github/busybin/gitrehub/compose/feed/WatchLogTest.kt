package io.github.busybin.gitrehub.compose.feed

import io.github.busybin.gitrehub.core.model.EventType
import io.github.busybin.gitrehub.core.model.RepoWatcher
import io.github.busybin.gitrehub.core.model.RepoWatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Watcher rows are synthesized: GitHub exposes neither an event nor a timestamp for a subscription,
 * so [WatchLog] diffs the current watcher list against the stored snapshot. The tests pin the rules
 * that keep the feed honest: the first snapshot backfills the watchers already there, later arrivals
 * become rows at the time they were noticed, and the account itself never appears.
 */
class WatchLogTest {

    private fun snapshot(repo: String, vararg logins: String) = RepoWatchers(
        repo = repo,
        totalCount = logins.size,
        watchers = logins.map { RepoWatcher(it, "https://avatars/$it") },
    )

    @Test
    fun theFirstSnapshotBackfillsTheExistingWatchers() {
        val state = WatchLog.fold(
            WatchState(),
            listOf(snapshot("me/a", "alice", "bob")),
            me = "me",
            now = 1_000L,
        )
        assertTrue(state.seeded)
        assertTrue(state.backfilled)
        assertEquals(setOf("me/a|alice", "me/a|bob"), state.records.map { it.key }.toSet())
        assertEquals(listOf("alice", "bob"), WatchLog.events(state.records).map { it.actor?.login })
        assertTrue(WatchLog.events(state.records).all { it.createdAt?.toEpochMilli() == 1_000L })
    }

    @Test
    fun anArrivalAfterTheSnapshotBecomesANewerRow() {
        val seeded = WatchLog.fold(WatchState(), listOf(snapshot("me/a", "alice")), "me", 1_000L)
        val updated = WatchLog.fold(seeded, listOf(snapshot("me/a", "alice", "carol")), "me", 2_000L)
        val rows = WatchLog.events(updated.records)
        assertEquals(listOf("carol", "alice"), rows.map { it.actor?.login })
        val row = rows.first()
        assertEquals(EventType.WatchStarted, row.type)
        assertEquals("https://avatars/carol", row.actor?.avatarUrl)
        assertEquals("me/a", row.repo?.fullName)
        assertEquals("a", row.repo?.name)
        assertEquals(2_000L, row.createdAt?.toEpochMilli())
        assertEquals(EventVerb.WATCH_STARTED, row.verb())
    }

    /** A refresh that adds nobody must not restamp the existing rows. */
    @Test
    fun unchangedSnapshotsKeepTheirTimes() {
        val seeded = WatchLog.fold(WatchState(), listOf(snapshot("me/a", "alice")), "me", 1_000L)
        val again = WatchLog.fold(seeded, listOf(snapshot("me/a", "alice")), "me", 5_000L)
        assertEquals(seeded, again)
        assertEquals(1_000L, WatchLog.events(again.records).single().createdAt?.toEpochMilli())
    }

    @Test
    fun myOwnSubscriptionIsNotAnEvent() {
        val seeded = WatchLog.fold(WatchState(), listOf(snapshot("me/a")), "me", 1_000L)
        val updated = WatchLog.fold(seeded, listOf(snapshot("me/a", "ME", "dave")), "me", 2_000L)
        assertEquals(listOf("dave"), WatchLog.events(updated.records).map { it.actor?.login })
    }

    /** An unfollow then re-follow must not produce the same row twice. */
    @Test
    fun aWatcherThatComesBackKeepsItsOriginalDetectionTime() {
        val seeded = WatchLog.fold(WatchState(), listOf(snapshot("me/a")), "me", 1_000L)
        val arrived = WatchLog.fold(seeded, listOf(snapshot("me/a", "erin")), "me", 2_000L)
        val left = WatchLog.fold(arrived, listOf(snapshot("me/a")), "me", 3_000L)
        val back = WatchLog.fold(left, listOf(snapshot("me/a", "erin")), "me", 4_000L)
        assertEquals(2_000L, WatchLog.events(back.records).single().createdAt?.toEpochMilli())
    }

    @Test
    fun rowsAreNewestFirstAndIdsAreStable() {
        val seeded = WatchLog.fold(WatchState(), listOf(snapshot("me/a")), "me", 1_000L)
        val first = WatchLog.fold(seeded, listOf(snapshot("me/a", "one")), "me", 2_000L)
        val second = WatchLog.fold(first, listOf(snapshot("me/a", "one", "two")), "me", 3_000L)
        val rows = WatchLog.events(second.records)
        assertEquals(listOf("two", "one"), rows.map { it.actor?.login })
        assertEquals(rows.map { it.id }, WatchLog.events(second.records).map { it.id })
        assertTrue(rows.all { it.id < 0L })
        assertNotEquals(rows[0].id, rows[1].id)
    }

    @Test
    fun aFailedAnswerDoesNotMakeEveryWatcherNew() {
        val seeded = WatchLog.fold(WatchState(), listOf(snapshot("me/a", "alice")), "me", 1_000L)
        val empty = WatchLog.fold(seeded, emptyList(), "me", 2_000L)
        val recovered = WatchLog.fold(empty, listOf(snapshot("me/a", "alice")), "me", 3_000L)
        assertEquals(1_000L, WatchLog.events(recovered.records).single().createdAt?.toEpochMilli())
    }

    /** An empty repo list must not be mistaken for "seeded, nobody watches anything". */
    @Test
    fun anEmptyAnswerDoesNotSeed() {
        val state = WatchLog.fold(WatchState(), emptyList(), "me", 1_000L)
        assertTrue(!state.seeded)
        val firstReal = WatchLog.fold(state, listOf(snapshot("me/a", "alice")), "me", 2_000L)
        assertTrue(firstReal.seeded)
        assertEquals(2_000L, WatchLog.events(firstReal.records).single().createdAt?.toEpochMilli())
    }

    /** State from the "seed only" build (firstSeen == 0) is stamped on the first refresh after upgrade. */
    @Test
    fun oldSeededStateIsBackfilledOnce() {
        val old = WatchState(
            seeded = true,
            backfilled = false,
            records = listOf(WatchRecord("me/a", "alice")),
        )
        val migrated = WatchLog.fold(old, listOf(snapshot("me/a", "alice")), "me", 7_000L)
        assertTrue(migrated.backfilled)
        assertEquals(7_000L, WatchLog.events(migrated.records).single().createdAt?.toEpochMilli())
        val later = WatchLog.fold(migrated, listOf(snapshot("me/a", "alice")), "me", 9_000L)
        assertEquals(7_000L, WatchLog.events(later.records).single().createdAt?.toEpochMilli())
    }
}
