package io.github.busybin.gitrehub.provider.rest.interceptors

import io.github.busybin.gitrehub.helper.PrefGetter
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * README images of a private repository live on `raw.githubusercontent.com`, which answers 404 to
 * anonymous requests, so the image loader has to send the token for those hosts only.
 */
class ImageAuthInterceptorTest {

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))
    }

    @After
    fun tearDown() {
        PrefGetter.token = null
        server.shutdown()
    }

    private fun request(path: String = "/owner/repo/main/assets/logo.png"): Request =
        Request.Builder().url(server.url(path)).build()

    @Test
    fun tokenIsSentToImageHosts() {
        PrefGetter.token = "abc123"
        client().newCall(request()).execute().close()
        assertEquals("token abc123", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun otherHostsAreLeftAlone() {
        PrefGetter.token = "abc123"
        client(hosts = emptySet()).newCall(request()).execute().close()
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun noTokenMeansNoHeader() {
        PrefGetter.token = null
        client().newCall(request()).execute().close()
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    private fun client(hosts: Set<String> = setOf(server.hostName)): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(ImageAuthInterceptor(hosts)).build()
}
