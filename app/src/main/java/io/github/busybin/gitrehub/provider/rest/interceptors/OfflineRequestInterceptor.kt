package io.github.busybin.gitrehub.provider.rest.interceptors

import io.github.busybin.gitrehub.helper.OfflinePrefs
import io.github.busybin.gitrehub.helper.OfflineStatus
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Serves the HTTP cache instead of the network when there is no connection.
 *
 * Two ways in, because "no network" is not always reported correctly by the system:
 *  - [isOnline] already knows we are offline → the request is rewritten to `only-if-cached`
 *    up front, so the user gets cached content instantly instead of waiting for a timeout;
 *  - the request still fails with an [IOException] (flaky connection, captive portal) → retried
 *    once, this time from the cache only.
 *
 * `max-stale` is what makes this work at all: responses are stored fresh for zero seconds (see
 * [CacheResponseInterceptor]), so stale data has to be explicitly acceptable. Only GET is touched —
 * writes can never be replayed from a cache.
 *
 * If nothing is cached, OkHttp answers `504 Unsatisfiable Request (only-if-cached)`, which the
 * modern UI turns into "offline: this page is not cached yet" (see `friendlyMessage`).
 */
class OfflineRequestInterceptor(
    private val isOnline: () -> Boolean = { OfflineStatus.isOnline },
    private val enabled: () -> Boolean = { OfflinePrefs.isEnabled },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" || !enabled()) return chain.proceed(request)

        if (!isOnline()) return chain.proceed(cacheOnly(request))

        return try {
            chain.proceed(request)
        } catch (e: IOException) {
            // The network dropped between the connectivity check and the actual call. Fall back to
            // whatever is on disk.
            val cached = try {
                chain.proceed(cacheOnly(request))
            } catch (_: IOException) {
                throw e
            }
            // A cache-only request never reaches the server, so a 504 here cannot be the server's
            // answer — it is OkHttp's synthetic `504 Unsatisfiable Request (only-if-cached)`, i.e.
            // "the cache had nothing". The real failure is the honest answer then; reporting
            // "offline, nothing cached" would hide a broken connection from the user.
            if (cached.code == HTTP_UNSATISFIABLE_REQUEST) {
                cached.close()
                throw e
            }
            cached
        }
    }

    private fun cacheOnly(request: Request): Request = request.newBuilder()
        .header("Cache-Control", "only-if-cached, max-stale=$MAX_STALE_SECONDS")
        .build()

    private companion object {
        /** How old a cached page may be and still be shown offline: 30 days. */
        const val MAX_STALE_SECONDS = 30L * 24 * 60 * 60

        /** What OkHttp answers when an `only-if-cached` request has nothing to serve. */
        const val HTTP_UNSATISFIABLE_REQUEST = 504
    }
}
