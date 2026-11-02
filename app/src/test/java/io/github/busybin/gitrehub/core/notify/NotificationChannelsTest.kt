package io.github.busybin.gitrehub.core.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationChannelsTest {

    @Test
    fun reasonMapsToItsGroup() {
        assertEquals(NotifyChannel.MENTION, NotifyChannel.of("mention"))
        assertEquals(NotifyChannel.MENTION, NotifyChannel.of("team_mention"))
        assertEquals(NotifyChannel.REVIEW, NotifyChannel.of("review_requested"))
        assertEquals(NotifyChannel.CI, NotifyChannel.of("ci_activity"))
        assertEquals(NotifyChannel.COMMENT, NotifyChannel.of("comment"))
        assertEquals(NotifyChannel.COMMENT, NotifyChannel.of("state_change"))
    }

    @Test
    fun reasonIsCaseInsensitive() {
        assertEquals(NotifyChannel.CI, NotifyChannel.of("CI_Activity"))
    }

    @Test
    fun unknownReasonFallsBackToOther() {
        assertEquals(NotifyChannel.OTHER, NotifyChannel.of(null))
        assertEquals(NotifyChannel.OTHER, NotifyChannel.of(""))
        assertEquals(NotifyChannel.OTHER, NotifyChannel.of("assign"))
        // A reason added by GitHub after this build must still produce a notification.
        assertEquals(NotifyChannel.OTHER, NotifyChannel.of("some_future_reason"))
    }

    @Test
    fun channelIdsAreStableAndUnique() {
        val ids = NotifyChannel.entries.map { it.channelId }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("gitrehub_mention", NotifyChannel.MENTION.channelId)
        assertEquals("gitrehub_other", NotifyChannel.OTHER.channelId)
    }

    @Test
    fun summaryIdsAreUniquePerChannel() {
        val ids = NotifyChannel.entries.map { NotifyChannel.summaryId(it) }
        assertEquals(ids.size, ids.toSet().size)
    }
}
