package io.github.busybin.gitrehub.core.net

import io.github.busybin.gitrehub.core.model.WatchLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/** The subscription body is the only place the two booleans are set; a swap here mutes a repo. */
class WatchSubscriptionBodyTest {

    @Test
    fun `all notifications`() {
        assertEquals(
            """{"subscribed":true,"ignored":false}""",
            watchSubscriptionBody(WatchLevel.ALL).toString(),
        )
    }

    @Test
    fun `ignored keeps the subscription but blocks it`() {
        assertEquals(
            """{"subscribed":false,"ignored":true}""",
            watchSubscriptionBody(WatchLevel.IGNORED).toString(),
        )
    }
}
