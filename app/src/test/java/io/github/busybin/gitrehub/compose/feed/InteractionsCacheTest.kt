package io.github.busybin.gitrehub.compose.feed

import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.EventActor
import io.github.busybin.gitrehub.core.model.EventPayload
import io.github.busybin.gitrehub.core.model.EventRepo
import io.github.busybin.gitrehub.core.model.EventSubject
import io.github.busybin.gitrehub.core.model.EventType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cached half of the feed's first page: the "actions on my repos" snapshot read back from disk
 * and merged with the cached event streams.
 */
class InteractionsCacheTest {

    private fun event(
        id: Long,
        type: EventType,
        login: String,
        repo: String,
        at: String,
        number: Int? = null,
    ) = Event(
        id = id,
        type = type,
        createdAt = Instant.parse(at),
        actor = EventActor(login = login, avatarUrl = "https://avatars/$login"),
        repo = EventRepo(name = repo.substringAfter('/'), fullName = repo),
        payload = EventPayload(pullRequest = number?.let { EventSubject(number = it, title = "t") }),
    )

    private val star = event(-11, EventType.WatchEvent, "decklong", "me/one", "2026-10-22T10:00:00Z")
    private val fork = event(-12, EventType.ForkEvent, "ntbowen", "me/one", "2026-10-22T09:00:00Z")
    private val push = event(101, EventType.PushEvent, "me", "me/one", "2026-10-22T11:00:00Z")

    @Test
    fun roundTripKeepsSyntheticRowsIntact() {
        val decoded = InteractionsCache.decode(InteractionsCache.encode(listOf(star, fork)))
        assertEquals(listOf(star, fork), decoded)
        assertTrue(decoded.all { it.id < 0 })
    }

    @Test
    fun unreadableOrMissingValuesAreJustNoCache() {
        assertEquals(emptyList<Event>(), InteractionsCache.decode(null))
        assertEquals(emptyList<Event>(), InteractionsCache.decode(""))
        assertEquals(emptyList<Event>(), InteractionsCache.decode("not json"))
        assertEquals(emptyList<Event>(), InteractionsCache.decode("""{"id":1}"""))
    }

    /** The cached snapshot is painted together with the cached streams; newest first. */
    @Test
    fun cachedInteractionsJoinTheEventsStream() {
        val merged = mergeFeedEvents(listOf(push), listOf(star, fork))
        assertEquals(listOf(push, star, fork), merged)
    }

    /** A watched repo delivers the same star through both sources: only the real event stays. */
    @Test
    fun aStarCarriedByBothSourcesAppearsOnce() {
        val realStar = event(202, EventType.WatchEvent, "decklong", "me/one", "2026-10-22T10:00:00Z")
        val merged = mergeFeedEvents(listOf(realStar), listOf(star, fork))
        assertEquals(listOf(realStar, fork), merged)
        assertNull(merged.firstOrNull { it.id == star.id })
    }
}
