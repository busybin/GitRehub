package io.github.busybin.gitrehub.core

import io.github.busybin.gitrehub.core.model.Event
import io.github.busybin.gitrehub.core.model.EventType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Decoding of the events API, pinned on a real response shape
 * (`gh api /users/busybin/received_events`): string ids, ISO instants, and the repo mini object
 * without `full_name`.
 */
class EventJsonTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun event(raw: String): Event = json.decodeFromString(Event.serializer(), raw)

    @Test
    fun stringIdsAndInstantsAreDecoded() {
        val e = event(
            """{"id":"24220601523","type":"PushEvent","created_at":"2026-10-15T20:38:53Z",
                "actor":{"login":"busybin","avatar_url":"https://avatars/busybin"},
                "repo":{"id":1413324684,"name":"busybin/gitrehub"},
                "payload":{"ref":"refs/heads/main","size":2,"commits":[{"sha":"abc","message":"m"}]},
                "public":true}""",
        )
        assertEquals(24220601523L, e.id)
        assertEquals(EventType.PushEvent, e.type)
        assertEquals(Instant.parse("2026-10-15T20:38:53Z"), e.createdAt)
        assertEquals("busybin", e.actor?.login)
        assertEquals("refs/heads/main", e.payload.ref)
        assertEquals(2, e.payload.size)
        assertEquals("abc", e.payload.commits.single().sha)
        assertEquals(true, e.isPublic)
    }

    @Test
    fun unknownTypesAndBrokenDatesDegradeInsteadOfFailing() {
        val e = event(
            """{"id":"1","type":"SomeFutureEvent","created_at":"not a date",
                "actor":null,"repo":null,"payload":{}}""",
        )
        assertEquals(EventType.Unknown, e.type)
        assertNull(e.createdAt)
        assertNull(e.repoFullName)
        assertNull(e.actor)
    }

    @Test
    fun numericIdsFromOtherServersStillDecode() {
        assertEquals(1001L, event("""{"id":1001,"type":"WatchEvent","payload":{}}""").id)
    }
}
