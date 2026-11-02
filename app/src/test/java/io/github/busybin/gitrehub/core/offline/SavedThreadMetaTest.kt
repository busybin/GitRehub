package io.github.busybin.gitrehub.core.offline

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedThreadMetaTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun keyIsStableForLookups() {
        val meta = SavedThreadMeta(account = "me", owner = "octocat", repo = "Hello-World", number = 42)
        assertEquals("octocat/Hello-World#42", meta.key)
        assertEquals("octocat/Hello-World", meta.repoFullName)
    }

    @Test
    fun indexEntriesRoundTripThroughJson() {
        val items = listOf(
            SavedThreadMeta(account = "me", owner = "o", repo = "r", number = 1, title = "t", savedAt = 10L),
            SavedThreadMeta(account = "me", owner = "o", repo = "r", number = 2, title = "u", savedAt = 20L),
        )
        val decoded = json.decodeFromString<List<SavedThreadMeta>>(json.encodeToString(items))
        assertEquals(items, decoded)
    }
}
