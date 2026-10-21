package io.github.busybin.gitrehub.core.notify

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The background poller's "already shown" set: ids survive a restart (MMKV) and never grow past the
 * cap, so one old notification cannot suppress a new thread forever.
 */
@RunWith(AndroidJUnit4::class)
class NotifiedThreadsTest {

    @Before
    fun setUp() = NotifiedThreads.clear()

    @After
    fun tearDown() = NotifiedThreads.clear()

    @Test
    fun remembersIdsAcrossCalls() {
        assertTrue(NotifiedThreads.ids().isEmpty())
        NotifiedThreads.remember(listOf(11L, 12L))
        assertTrue(11L in NotifiedThreads.ids())
        assertTrue(12L in NotifiedThreads.ids())
        assertFalse(13L in NotifiedThreads.ids())

        NotifiedThreads.remember(listOf(13L))
        assertEquals(setOf(11L, 12L, 13L), NotifiedThreads.ids())
    }

    @Test
    fun keepsOnlyTheNewestIdsBeyondTheCap() {
        NotifiedThreads.remember((1L..2100L).toList())
        val ids = NotifiedThreads.ids()
        assertEquals(2000, ids.size)
        assertFalse(1L in ids)
        assertTrue(2100L in ids)
    }
}
