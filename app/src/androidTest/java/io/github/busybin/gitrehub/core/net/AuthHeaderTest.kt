package io.github.busybin.gitrehub.core.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.helper.PrefGetter
import io.github.busybin.gitrehub.provider.rest.HttpProvider
import io.github.busybin.gitrehub.provider.rest.interceptors.AuthenticationInterceptor
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every request the app makes must carry the signed-in account's token.
 *
 * Regression: `GitHubClient`'s shared client was built without an `AuthenticationInterceptor`, so
 * after the rewrite public endpoints worked while `/user`, `/user/repos` and notifications answered
 * 401 — invisible to the mock smoke tests, which ignore the header.
 *
 * The request is answered by an interceptor so no socket (and no cleartext policy) is involved.
 */
@RunWith(AndroidJUnit4::class)
class AuthHeaderTest {

    private class Capture : Interceptor {
        var authorization: String? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            authorization = chain.request().header("Authorization")
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody(null))
                .build()
        }
    }

    @Before
    fun setUp() {
        PrefGetter.token = "session-token"
    }

    @After
    fun tearDown() = PrefGetter.clearCredentials()

    @Test
    fun defaultClientSendsTheSessionToken() {
        val capture = Capture()
        HttpProvider.provideOkHttpClient(capture)
            .newCall(Request.Builder().url("https://api.github.com/user").build())
            .execute()
            .close()

        assertEquals("token session-token", capture.authorization)
    }

    @Test
    fun anExplicitTokenWinsOverTheSession() {
        val capture = Capture()
        HttpProvider.provideOkHttpClient(AuthenticationInterceptor("other-token"), capture)
            .newCall(Request.Builder().url("https://api.github.com/user").build())
            .execute()
            .close()

        assertEquals("token other-token", capture.authorization)
    }
}
