package io.github.busybin.gitrehub.compose.feed

import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.EventPayload
import io.github.busybin.gitrehub.core.model.EventSubject
import io.github.busybin.gitrehub.core.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a feed row says, especially for the case the user caught: a PR that *somebody else* opened
 * and *you* merged used to read "you opened a pull request".
 */
class FeedVerbTest {

    private fun pr(action: String?, merged: Boolean = false, number: Int = 2) = Event(
        type = EventType.PullRequestEvent,
        payload = EventPayload(
            action = action,
            pullRequest = EventSubject(number = number, merged = merged),
        ),
    )

    private fun issue(action: String?, number: Int = 7) = Event(
        type = EventType.IssuesEvent,
        payload = EventPayload(action = action, issue = EventSubject(number = number)),
    )

    @Test
    fun mergingSomebodyElsesPullRequestDoesNotSayItWasOpened() {
        assertEquals(EventVerb.PR_MERGED, pr("closed", merged = true).verb())
    }

    @Test
    fun closingWithoutMergingSaysClosed() {
        assertEquals(EventVerb.PR_CLOSED, pr("closed").verb())
    }

    @Test
    fun openingStillSaysOpened() {
        assertEquals(EventVerb.PR_OPENED, pr("opened").verb())
    }

    @Test
    fun reopeningAndReviewRequestsAreDistinguished() {
        assertEquals(EventVerb.PR_REOPENED, pr("reopened").verb())
        assertEquals(EventVerb.PR_REVIEW_REQUESTED, pr("review_requested").verb())
    }

    @Test
    fun issuesFollowTheirActionToo() {
        assertEquals(EventVerb.ISSUE_OPENED, issue("opened").verb())
        assertEquals(EventVerb.ISSUE_CLOSED, issue("closed").verb())
    }

    @Test
    fun starsAndForksReadAsTheUserExpects() {
        assertEquals(EventVerb.STAR, Event(type = EventType.WatchEvent).verb())
        assertEquals(EventVerb.FORK, Event(type = EventType.ForkEvent).verb())
    }

    @Test
    fun anUnknownEventStillSaysSomething() {
        assertEquals(EventVerb.NEW, Event(type = EventType.Unknown).verb())
        // GitHub adds event types now and then; decoding an unknown one must not fail the stream.
        assertEquals(EventType.Unknown, EventType.from("SomeFutureEvent"))
        assertEquals(EventType.Unknown, EventType.from(null))
    }

    @Test
    fun numberAndTitleAreOnlyClaimedWhenThereIsOne() {
        assertEquals("#2", pr("opened").numberLabel())
        assertNull(Event(type = EventType.WatchEvent).numberLabel())

        val titled = Event(
            type = EventType.PullRequestEvent,
            payload = EventPayload(
                action = "opened",
                pullRequest = EventSubject(number = 2, title = "i18n: add Simplified Chinese"),
            ),
        )
        assertEquals("i18n: add Simplified Chinese", titled.titleLine())
        assertNull(pr("opened").titleLine())
        assertNull(Event(type = EventType.WatchEvent).titleLine())
    }
}
