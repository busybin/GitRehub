package io.github.busybin.gitrehub.compose

import io.github.busybin.gitrehub.provider.rest.interceptors.CacheResponseInterceptor
import io.github.busybin.gitrehub.provider.rest.interceptors.OfflineRequestInterceptor
import okhttp3.Cache
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * The offline HTTP cache, against a real (local) server and a real on-disk cache.
 *
 * Only a true end-to-end run shows the thing that matters: GitHub answers with
 * `Cache-Control: private, max-age=60` on authenticated requests, and OkHttp **refuses to store
 * those at all** — so "just add a `Cache`" would silently keep an empty cache forever. The
 * interceptors are what make the entries exist; these tests are what keep them honest.
 *
 * The interceptors reach Android only through injected lambdas, so this runs on the JVM in about a
 * second instead of on an emulator.
 */
class OfflineCacheTest {

    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File
    private var online = true

    /** What the caller sees. The body is always drained — see [call]. */
    private data class Reply(val code: Int, val body: String)

    private val cache by lazy { Cache(cacheDir, 4L * 1024 * 1024) }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        cacheDir = File.createTempFile("gitrehub-http-cache", "").let {
            it.delete()
            it.mkdirs()
            it
        }
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        cacheDir.deleteRecursively()
    }

    private fun client() = OkHttpClient.Builder()
        .cache(cache)
        .addNetworkInterceptor(CacheResponseInterceptor(enabled = { true }))
        .addInterceptor(OfflineRequestInterceptor(isOnline = { online }, enabled = { true }))
        .build()

    /**
     * Executes [request] and drains the body.
     *
     * Draining is not incidental: OkHttp only commits a cache entry once the response body has been
     * read to the end, so a test that closed early would be checking an empty cache.
     */
    private fun call(client: OkHttpClient, request: Request): Reply =
        client.newCall(request).execute().use { Reply(it.code, it.body?.string().orEmpty()) }

    private fun get(client: OkHttpClient, path: String, token: String? = null): Reply =
        call(
            client,
            Request.Builder()
                .url(server.url(path))
                .apply { token?.let { header("Authorization", "token $it") } }
                .build(),
        )

    private fun post(client: OkHttpClient, path: String, token: String): Reply =
        call(
            client,
            Request.Builder()
                .url(server.url(path))
                .header("Authorization", "token $token")
                .post(FormBody.Builder().add("title", "hi").build())
                .build(),
        )

    /** GitHub's own shape: an authenticated answer OkHttp would refuse to store as-is. */
    private fun githubLike(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setHeader("Cache-Control", "private, max-age=60")
        .setHeader("ETag", "\"etag-$body\"")
        .setHeader("Vary", "Authorization")
        .setBody(body)

    @Test
    fun aFetchedPageIsReplayedWhileOffline() {
        val client = client()
        server.enqueue(githubLike("""{"login":"busybin"}"""))

        val first = get(client, "/user", token = "token-a")
        assertEquals(200, first.code)
        assertEquals("""{"login":"busybin"}""", first.body)
        assertEquals(1, server.requestCount)

        online = false
        val offline = get(client, "/user", token = "token-a")
        assertEquals(200, offline.code)
        assertEquals("""{"login":"busybin"}""", offline.body)
        // served from disk: the server was not asked again
        assertEquals(1, server.requestCount)
    }

    /** Nothing was ever stored (a POST, a 404, …): say so instead of inventing a page. */
    @Test
    fun anUncachedPageFailsCleanlyWhileOffline() {
        online = false
        val reply = get(client(), "/user/repos", token = "token-a")
        assertEquals(504, reply.code)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun postIsNeverReplayedFromTheCache() {
        val client = client()
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"number":1}"""))
        assertEquals(201, post(client, "/repos/a/b/issues", "token-a").code)

        // Even with the connection "down", a write goes to the network — a cache cannot replay it.
        online = false
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"number":2}"""))
        val second = post(client, "/repos/a/b/issues", "token-a")
        assertEquals(201, second.code)
        assertEquals("""{"number":2}""", second.body)
        assertEquals(2, server.requestCount)
    }

    /**
     * One URL, one entry: a second account answering that URL *replaces* the first account's page
     * (OkHttp keys the cache on `md5(url)`, and `Vary` only decides whether the stored page may be
     * reused). So the wrong account must get nothing rather than somebody else's private data —
     * and the app clears the whole cache on an account switch as well (`HttpCache.clear()`).
     */
    @Test
    fun anotherAccountsPageIsNeverServedFromTheCache() {
        val client = client()
        server.enqueue(githubLike("""{"login":"account-a"}"""))
        assertEquals("""{"login":"account-a"}""", get(client, "/user", token = "token-a").body)
        server.enqueue(githubLike("""{"login":"account-b"}"""))
        assertEquals("""{"login":"account-b"}""", get(client, "/user", token = "token-b").body)

        online = false
        // account-a's page is gone; account-b's page must not be handed over instead
        assertEquals(504, get(client, "/user", token = "token-a").code)
        // an account that never fetched anything must not see it either
        assertEquals(504, get(client, "/user", token = "token-c").code)
        // while the account that did fetch it still gets its page offline
        assertEquals("""{"login":"account-b"}""", get(client, "/user", token = "token-b").body)
    }

    /** With the setting off, the response is still delivered — it just never lands on disk. */
    @Test
    fun cachingCanBeSwitchedOff() {
        val cache = Cache(cacheDir, 4L * 1024 * 1024)
        val client = OkHttpClient.Builder()
            .cache(cache)
            .addNetworkInterceptor(CacheResponseInterceptor(enabled = { false }))
            .addInterceptor(OfflineRequestInterceptor(isOnline = { online }, enabled = { false }))
            .build()
        server.enqueue(githubLike("""{"login":"busybin"}"""))

        val reply = get(client, "/user", token = "token-a")
        assertEquals(200, reply.code)
        assertEquals("""{"login":"busybin"}""", reply.body)
        cache.flush()
        assertEquals(0L, cache.size())
    }

    /**
     * The connection dropped without Android noticing (flaky network, captive portal): the cached
     * page has to be served instead of an error.
     */
    @Test
    fun aBrokenConnectionFallsBackToTheCachedPage() {
        val client = client()
        server.enqueue(githubLike("""{"login":"busybin"}"""))
        assertEquals(200, get(client, "/user", token = "token-a").code)

        // `online` is still true — the app cannot tell, the connect attempt just fails
        server.shutdown()
        val reply = get(client, "/user", token = "token-a")
        assertEquals(200, reply.code)
        assertEquals("""{"login":"busybin"}""", reply.body)
    }

    /**
     * …but when there is nothing to fall back on, the real failure has to reach the caller: a fake
     * "offline, nothing cached" would hide a broken connection behind a wrong explanation.
     */
    @Test
    fun aNetworkFailureIsNotMaskedWhenNothingIsCached() {
        val client = client()
        server.shutdown()
        assertThrows(IOException::class.java) {
            get(client, "/user", token = "token-a")
        }
    }
}
