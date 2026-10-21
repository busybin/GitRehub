package io.github.busybin.gitrehub.compose.common

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Avatars keep a stable URL when the picture is replaced, so the change is only detectable through
 * the ETag. A `304` must leave the cached picture alone; a different ETag must trigger a refetch.
 */
class AvatarRevalidatorTest {

    private lateinit var server: MockWebServer
    private val etags = mutableMapOf<String, String>()
    private lateinit var revalidator: AvatarRevalidator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        revalidator = AvatarRevalidator(
            client = OkHttpClient(),
            etagOf = { etags[it] },
            rememberEtag = { url, etag -> etags[url] = etag },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun firstSightStoresTheEtagWithoutReportingAChange() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"one\""))
        val url = server.url("/u/1").toString()

        assertFalse(revalidator.changed(url))
        assertEquals("\"one\"", etags[url])
    }

    @Test
    fun notModifiedKeepsTheCachedPicture() {
        val url = server.url("/u/1").toString()
        etags[url] = "\"one\""
        server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "\"one\""))

        assertFalse(revalidator.changed(url))
        assertEquals("\"one\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun differentEtagReportsAChange() {
        val url = server.url("/u/1").toString()
        etags[url] = "\"one\""
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"two\""))

        assertTrue(revalidator.changed(url))
        assertEquals("\"two\"", etags[url])
    }

    @Test
    fun failedRequestChangesNothing() {
        val url = server.url("/u/1").toString()
        etags[url] = "\"one\""
        server.enqueue(MockResponse().setResponseCode(500))

        assertFalse(revalidator.changed(url))
        assertEquals("\"one\"", etags[url])
    }
}
