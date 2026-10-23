package io.github.busybin.gitrehub.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.compose.feed.InteractionsCache
import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.EventActor
import io.github.busybin.gitrehub.core.model.EventRepo
import io.github.busybin.gitrehub.core.model.EventType
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The disk half of the feed's "actions on my repos" cache: what the Activity tab paints before the
 * network refresh comes back.
 */
@RunWith(AndroidJUnit4::class)
class InteractionsCachePersistTest {

    private val star = Event(
        id = -5,
        type = EventType.WatchEvent,
        createdAt = Instant.parse("2026-10-22T10:00:00Z"),
        actor = EventActor(login = "decklong", avatarUrl = "https://avatars/decklong"),
        repo = EventRepo(name = "one", fullName = "me/one"),
    )

    @After
    fun tearDown() {
        InteractionsCache.save("me", emptyList())
        InteractionsCache.save("someone-else", emptyList())
    }

    @Test
    fun aSnapshotIsStoredPerLogin() {
        InteractionsCache.save("me", listOf(star))
        assertEquals(listOf(star), InteractionsCache.load("me"))
        // Another account must not inherit these rows.
        assertEquals(emptyList<Event>(), InteractionsCache.load("someone-else"))
    }

    @Test
    fun anEmptySnapshotClearsTheOldOneInsteadOfLeavingStaleStars() {
        InteractionsCache.save("me", listOf(star))
        InteractionsCache.save("me", emptyList())
        assertEquals(emptyList<Event>(), InteractionsCache.load("me"))
    }
}
