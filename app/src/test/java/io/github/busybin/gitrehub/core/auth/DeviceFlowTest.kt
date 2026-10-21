package io.github.busybin.gitrehub.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the device flow's response handling.
 *
 * Only the parsing is covered here (the HTTP calls need a network); the point is the states the UI
 * reacts to: "keep waiting", "slow down", "the code expired", "the user said no", "we got a token"
 * and the failure GitHub sends when the OAuth App has Device Flow switched off.
 */
class DeviceFlowTest {

    @Test
    fun `challenge parses the fields the screen needs`() {
        val challenge = DeviceFlow.challengeFrom(
            mapOf(
                "device_code" to "3584d83530557fdd1f46af8289938c8ef79f9dc5",
                "user_code" to "WDJB-MJHT",
                "verification_uri" to "https://github.com/login/device",
                "expires_in" to "900",
                "interval" to "5",
            )
        )

        assertEquals("3584d83530557fdd1f46af8289938c8ef79f9dc5", challenge.deviceCode)
        assertEquals("WDJB-MJHT", challenge.userCode)
        assertEquals("https://github.com/login/device", challenge.verificationUri)
        assertEquals(900, challenge.expiresInSeconds)
        assertEquals(5, challenge.intervalSeconds)
    }

    @Test
    fun `challenge falls back to sane defaults when github is terse`() {
        val challenge = DeviceFlow.challengeFrom(mapOf("device_code" to "d", "user_code" to "u"))

        assertEquals("https://github.com/login/device", challenge.verificationUri)
        assertEquals(900, challenge.expiresInSeconds)
        assertEquals(5, challenge.intervalSeconds)
    }

    @Test
    fun `challenge rejects an answer without a code`() {
        val failure = assertThrows(DeviceFlow.Failure::class.java) {
            DeviceFlow.challengeFrom(mapOf("device_code" to "d"))
        }
        assertEquals(DeviceFlow.Reason.NO_CODE, failure.reason)
    }

    /** GitHub's answer for an app without Device Flow enabled; the message must be actionable. */
    @Test
    fun `challenge explains a disabled device flow`() {
        val failure = assertThrows(DeviceFlow.Failure::class.java) {
            DeviceFlow.challengeFrom(
                mapOf(
                    "error" to "device_flow_disabled",
                    "error_description" to "Device Flow must be explicitly enabled for this App",
                )
            )
        }
        assertEquals(DeviceFlow.Reason.DEVICE_FLOW_DISABLED, failure.reason)
    }

    @Test
    fun `poll maps a token`() {
        val poll = DeviceFlow.pollFrom(
            mapOf("access_token" to "gho_abc", "token_type" to "bearer", "scope" to "repo")
        )
        assertEquals(DeviceFlow.Poll.Token("gho_abc"), poll)
    }

    @Test
    fun `poll maps the lifecycle errors`() {
        assertEquals(DeviceFlow.Poll.Pending, DeviceFlow.pollFrom(mapOf("error" to "authorization_pending")))
        assertEquals(DeviceFlow.Poll.SlowDown, DeviceFlow.pollFrom(mapOf("error" to "slow_down")))
        assertEquals(DeviceFlow.Poll.Expired, DeviceFlow.pollFrom(mapOf("error" to "expired_token")))
        assertEquals(DeviceFlow.Poll.Denied, DeviceFlow.pollFrom(mapOf("error" to "access_denied")))
    }

    @Test
    fun `poll surfaces an unknown error with github's description`() {
        val poll = DeviceFlow.pollFrom(
            mapOf("error" to "incorrect_client_credentials", "error_description" to "The client_id is not valid")
        )
        assertTrue(poll is DeviceFlow.Poll.Error)
        assertEquals(
            DeviceFlow.Reason.CLIENT_ID_INVALID,
            (poll as DeviceFlow.Poll.Error).reason,
        )
    }

    @Test
    fun `poll complains when a 200 carries no token`() {
        val poll = DeviceFlow.pollFrom(mapOf("token_type" to "bearer"))
        assertTrue(poll is DeviceFlow.Poll.Error)
        assertEquals(DeviceFlow.Reason.NO_TOKEN, (poll as DeviceFlow.Poll.Error).reason)
    }
}
