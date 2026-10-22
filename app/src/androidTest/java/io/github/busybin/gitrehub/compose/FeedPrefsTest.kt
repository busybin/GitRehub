package io.github.busybin.gitrehub.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.compose.common.ReadState
import io.github.busybin.gitrehub.compose.common.ThreadKey
import io.github.busybin.gitrehub.compose.feed.FeedPrefs
import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.EventPayload
import io.github.busybin.gitrehub.core.model.EventRelease
import io.github.busybin.gitrehub.core.model.EventRepo
import io.github.busybin.gitrehub.core.model.EventSubject
import io.github.busybin.gitrehub.helper.PrefHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Feed filtering rules (keyword blocking + hide-read switch).
 *
 * Read bookkeeping itself is covered by [ReadStateTest]; it is shared with the notifications screen.
 */
@RunWith(AndroidJUnit4::class)
class FeedPrefsTest {

    @After
    fun tearDown() {
        FeedPrefs.setBlocked("")
        FeedPrefs.setHideRead(false)
        ReadState.clear()
    }

    @Test
    fun keywordsBlockMatchingAuthorsAndRepos() {
        FeedPrefs.setBlocked("spammer, Some/Repo")
        assertEquals(listOf("spammer", "Some/Repo"), FeedPrefs.keywordList())

        assertTrue(FeedPrefs.isBlocked("spammer", null))
        assertTrue(FeedPrefs.isBlocked("someone", "some/repo"))
        assertFalse(FeedPrefs.isBlocked("octocat", "octocat/Hello-World"))
        // blank keywords never block anything
        FeedPrefs.setBlocked("")
        assertFalse(FeedPrefs.isBlocked("spammer", "some/repo"))
    }

    @Test
    fun hideReadIsPersisted() {
        FeedPrefs.setHideRead(true)
        assertTrue(FeedPrefs.hideRead.value)
        FeedPrefs.setHideRead(false)
        assertFalse(FeedPrefs.hideRead.value)
    }
}

/**
 * Shared read state: event ids plus thread keys, which is what makes the activity feed and the
 * notifications screen agree on what has already been read.
 */
@RunWith(AndroidJUnit4::class)
class ReadStateTest {

    @After
    fun tearDown() {
        ReadState.clear()
    }

    @Test
    fun readIdsAreTrackedAndCleared() {
        ReadState.clear()
        assertTrue(ReadState.readIds.value.isEmpty())

        ReadState.markRead(42)
        ReadState.markRead(43)
        assertEquals(setOf(42L, 43L), ReadState.readIds.value)

        // marking twice must not duplicate or reorder
        ReadState.markRead(42)
        assertEquals(setOf(42L, 43L), ReadState.readIds.value)

        ReadState.markAllRead(listOf(44, 45))
        assertEquals(setOf(42L, 43L, 44L, 45L), ReadState.readIds.value)

        ReadState.clear()
        assertTrue(ReadState.readIds.value.isEmpty())
    }

    @Test
    fun threadKeysAreSharedByBothScreens() {
        ReadState.clear()

        // what the notifications screen records when a thread is swiped read
        ReadState.markReadKey(ThreadKey.fromApiUrl("https://api.github.com/repos/octocat/Hello-World/issues/42"))
        assertEquals(setOf("octocat/Hello-World#i42"), ReadState.readKeys.value)

        // the feed event about that very issue now counts as read too
        val event = eventOf("octocat/Hello-World") { it.copy(issue = EventSubject(number = 42)) }
        assertTrue(ReadState.isRead(event.id, ThreadKey.ofEvent(event)))
        // ...while an unrelated event stays unread
        val other = eventOf("octocat/Hello-World") { it.copy(issue = EventSubject(number = 7)) }
        assertFalse(ReadState.isRead(other.id, ThreadKey.ofEvent(other)))

        ReadState.markReadKeys(listOf("a/b#p1", "c/d#r9", ""))
        assertEquals(setOf("octocat/Hello-World#i42", "a/b#p1", "c/d#r9"), ReadState.readKeys.value)

        ReadState.clear()
        assertTrue(ReadState.readKeys.value.isEmpty())
        assertTrue(ReadState.readIds.value.isEmpty())
    }

    @Test
    fun pushKeysAreTrackedAndCleared() {
        ReadState.clear()
        assertTrue(ReadState.readPushes.value.isEmpty())

        ReadState.markReadPush("push:9001")
        assertEquals(setOf("push:9001"), ReadState.readPushes.value)
        ReadState.markReadPushes(listOf("push:9002", " "))
        assertEquals(setOf("push:9001", "push:9002"), ReadState.readPushes.value)

        ReadState.clear()
        assertTrue(ReadState.readPushes.value.isEmpty())
        assertNull(PrefHelper.getString("m3_feed_read_pushes"))
    }

    @Test
    fun removingTheLastReadKeyClearsTheStoredValue() {
        ReadState.clear()
        ReadState.markReadKey("octocat/Hello-World#i42")
        assertEquals(setOf("octocat/Hello-World#i42"), ReadState.readKeys.value)

        ReadState.unmarkReadKey("octocat/Hello-World#i42")
        assertTrue(ReadState.readKeys.value.isEmpty())
        // Must clear on disk too: an empty set must not go through putAny(""), which treats "" as
        // "do not store" and would let the old read records come back on next launch — same trap as
        // LanguagePrefs.
        assertNull(PrefHelper.getString("m3_read_thread_keys"))
    }

    @Test
    fun threadKeysAreParsedFromNotificationUrlsAndEventPayloads() {        assertEquals(
            "octocat/Hello-World#i42",
            ThreadKey.fromApiUrl("https://api.github.com/repos/octocat/Hello-World/issues/42"),
        )
        assertEquals(
            "octocat/Hello-World#p7",
            ThreadKey.fromApiUrl("https://api.github.com/repos/octocat/Hello-World/pulls/7/"),
        )
        assertEquals(
            "octocat/Hello-World#r13",
            ThreadKey.fromApiUrl("https://api.github.com/repos/octocat/Hello-World/releases/13"),
        )
        // thread types we do not map, plus junk
        assertNull(ThreadKey.fromApiUrl("https://api.github.com/repos/octocat/Hello-World/discussions/2"))
        assertNull(ThreadKey.fromApiUrl("https://api.github.com/repos/octocat/Hello-World"))
        assertNull(ThreadKey.fromApiUrl(null))
        assertNull(ThreadKey.of("octocat/Hello-World", ThreadKey.ISSUE, 0))

        val pr = eventOf("octocat/Hello-World") { it.copy(pullRequest = EventSubject(number = 7)) }
        assertEquals("octocat/Hello-World#p7", ThreadKey.ofEvent(pr))
        val release = eventOf("octocat/Hello-World") { it.copy(release = EventRelease(id = 13)) }
        assertEquals("octocat/Hello-World#r13", ThreadKey.ofEvent(release))
        // a push has no thread to match on
        assertNull(ThreadKey.ofEvent(eventOf("octocat/Hello-World") { it.copy(ref = "refs/heads/main") }))
    }

    private fun eventOf(repo: String, payload: (EventPayload) -> EventPayload): Event = Event(
        id = 1,
        repo = EventRepo(fullName = repo),
        payload = payload(EventPayload()),
    )
}
