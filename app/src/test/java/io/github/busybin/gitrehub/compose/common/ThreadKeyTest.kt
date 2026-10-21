package io.github.busybin.gitrehub.compose.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadKeyTest {

    private val issueUrl = "https://api.github.com/repos/octocat/Hello-World/issues/42"
    private val key = "octocat/Hello-World#i42"

    @Test
    fun unknownThreadIsNotRead() {
        assertFalse(ThreadKey.isReadLocally(null, 1_000L, setOf(key), emptyMap()))
        assertFalse(
            ThreadKey.isReadLocally("https://api.github.com/repos/o/r/commits/abc", 1_000L, setOf(key), emptyMap()),
        )
    }

    @Test
    fun recordedKeyCountsAsRead() {
        assertTrue(ThreadKey.isReadLocally(issueUrl, 1_000L, setOf(key), mapOf(key to 2_000L)))
    }

    @Test
    fun legacyKeyWithoutTimestampCountsAsRead() {
        assertTrue(ThreadKey.isReadLocally(issueUrl, 1_000L, setOf(key), emptyMap()))
    }

    @Test
    fun newerActivityMakesTheThreadUnreadAgain() {
        assertFalse(ThreadKey.isReadLocally(issueUrl, 3_000L, setOf(key), mapOf(key to 2_000L)))
        assertTrue(ThreadKey.isReadLocally(issueUrl, 2_000L, setOf(key), mapOf(key to 2_000L)))
    }

    @Test
    fun readTimeOfAnotherThreadDoesNotLeak() {
        assertFalse(
            ThreadKey.isReadLocally(issueUrl, 1_000L, setOf(key), mapOf("other/repo#p7" to 9_000L)),
        )
    }
}
