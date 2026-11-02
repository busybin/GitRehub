package io.github.busybin.gitrehub.compose.issues

import io.github.busybin.gitrehub.core.model.Comment
import io.github.busybin.gitrehub.core.model.TimelineEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TimelineMergeTest {

    private fun at(second: Long): Instant = Instant.ofEpochSecond(second)

    private fun comment(id: Long, second: Long) =
        Comment(id = id, body = "c$id", createdAt = at(second))

    private fun event(id: Long, name: String, second: Long) =
        TimelineEvent(id = id, event = name, createdAt = at(second))

    @Test
    fun commentsAndEventsInterleaveByTime() {
        val rows = mergeTimeline(
            comments = listOf(comment(1, 100), comment(2, 300)),
            events = listOf(event(9, "labeled", 200), event(8, "closed", 400)),
        )
        assertEquals(listOf("c1", "e9:labeled", "c2", "e8:closed"), rows.map { it.key })
    }

    @Test
    fun unknownEventsAreDropped() {
        val rows = mergeTimeline(
            comments = listOf(comment(1, 100)),
            events = listOf(event(2, "some_future_event", 150)),
        )
        assertEquals(1, rows.size)
        assertNull(rows[0].event)
    }

    @Test
    fun unsupportedBookkeepingEventsAreDropped() {
        val rows = mergeTimeline(
            comments = emptyList(),
            events = listOf(event(3, "commented", 10), event(4, "reviewed", 20)),
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun tieIsBrokenByKeySoOrderIsStable() {
        val rows = mergeTimeline(
            comments = listOf(comment(5, 100)),
            events = listOf(event(1, "closed", 100)),
        )
        // "c5" sorts before "e1" — deterministic, not insertion order.
        assertEquals(listOf("c5", "e1:closed"), rows.map { it.key })
    }

    @Test
    fun missingTimestampsStillProduceRows() {
        val rows = mergeTimeline(
            comments = listOf(Comment(id = 1, body = "x")),
            events = listOf(TimelineEvent(id = 2, event = "closed")),
        )
        assertEquals(2, rows.size)
        assertEquals(0L, rows[0].at)
    }
}
