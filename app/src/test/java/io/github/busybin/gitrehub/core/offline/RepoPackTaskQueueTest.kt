package io.github.busybin.gitrehub.core.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepoPackTaskQueueTest {

    private fun task(
        repo: String,
        includeIssues: Boolean = false,
    ) = RepoPackTask("me", repo, RepoPackOptions(includeIssues = includeIssues))

    @Test
    fun tasksRunInTheOrderTheyWereQueued() {
        val queue = RepoPackTaskQueue()
        queue.enqueue(task("a"))
        queue.enqueue(task("b"))
        assertEquals(listOf("me/a", "me/b"), queue.snapshot().map { it.fullName })
        assertEquals("me/a", queue.poll()?.fullName)
        assertEquals("me/b", queue.poll()?.fullName)
        assertNull(queue.poll())
    }

    @Test
    fun queuingTheSameRepositoryReplacesItsOptionsInPlace() {
        val queue = RepoPackTaskQueue()
        queue.enqueue(task("a"))
        queue.enqueue(task("b", includeIssues = false))
        queue.enqueue(task("b", includeIssues = true))
        val snapshot = queue.snapshot()
        assertEquals(listOf("me/a", "me/b"), snapshot.map { it.fullName })
        assertEquals(true, snapshot[1].options.includeIssues)
    }

    @Test
    fun clearDropsEverything() {
        val queue = RepoPackTaskQueue()
        queue.enqueue(task("a"))
        queue.clear()
        assertEquals(emptyList<String>(), queue.snapshot().map { it.fullName })
    }
}
