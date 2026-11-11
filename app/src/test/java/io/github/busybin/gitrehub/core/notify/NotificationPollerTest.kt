package io.github.busybin.gitrehub.core.notify

import io.github.busybin.gitrehub.core.model.Notification
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Multi-account merging: the poller watches every stored account, but a thread watched by two of
 * them is still one thing in the shade and one number on the badge.
 */
class NotificationPollerTest {

    private fun thread(id: Long, unread: Boolean = true) =
        Notification(id = id.toString(), unread = unread)

    @Test
    fun unreadKeepsAccountOrderAndRespectsTheLimit() {
        val inboxes = listOf(
            NotificationPoller.Inbox("alice", listOf(thread(1), thread(2, unread = false), thread(3))),
            NotificationPoller.Inbox("bob", listOf(thread(4))),
        )
        val fresh = NotificationPoller.unread(inboxes, limit = 2)
        assertEquals(listOf("alice" to 1L, "alice" to 3L), fresh.map { it.first to it.second.numericId })
    }

    @Test
    fun aThreadWatchedByTwoAccountsIsNotifiedOnce() {
        val inboxes = listOf(
            NotificationPoller.Inbox("alice", listOf(thread(7))),
            NotificationPoller.Inbox("bob", listOf(thread(7), thread(8))),
        )
        val fresh = NotificationPoller.unread(inboxes, limit = 10)
        assertEquals(listOf(7L, 8L), fresh.map { it.second.numericId })
        // Current account first, so the surviving entry belongs to it.
        assertEquals("alice", fresh.first().first)
    }

    @Test
    fun alreadyShownThreadsAreSkipped() {
        val inboxes = listOf(NotificationPoller.Inbox("alice", listOf(thread(7), thread(8))))
        val fresh = NotificationPoller.unread(inboxes, limit = 10, skip = setOf(7L))
        assertEquals(listOf(8L), fresh.map { it.second.numericId })
    }

    @Test
    fun staleOnlyContainsRowsThatAreNoLongerUnread() {
        // 7 was posted and is still unread, 8 was read elsewhere, 9 was never posted.
        assertEquals(setOf(8L), NotificationPoller.stale(setOf(7L, 8L), setOf(7L, 9L)))
        assertEquals(emptySet<Long>(), NotificationPoller.stale(emptySet(), setOf(1L)))
    }

    @Test
    fun unreadCountCountsASharedThreadOnce() {
        val inboxes = listOf(
            NotificationPoller.Inbox("alice", listOf(thread(1), thread(2))),
            NotificationPoller.Inbox("bob", listOf(thread(2), thread(3), thread(4, unread = false))),
        )
        assertEquals(3, NotificationPoller.unreadCount(inboxes))
    }
}
