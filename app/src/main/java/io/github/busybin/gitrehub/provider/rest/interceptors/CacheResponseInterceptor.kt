package io.github.busybin.gitrehub.provider.rest.interceptors

import io.github.busybin.gitrehub.helper.OfflinePrefs
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Makes GitHub's read-only responses *storable* so they can be replayed offline.
 *
 * OkHttp refuses to store a response to a request that carries an `Authorization` header unless the
 * response is explicitly `public` — and every GitHub API answer we care about is
 * `Cache-Control: private, max-age=60`. Without this interceptor the disk cache would stay empty
 * and "offline mode" would never have anything to show.
 *
 * Two things happen here:
 *  - `public` is added so the cache accepts the entry at all,
 *  - the freshness lifetime is set to **0** on purpose.
 *
 * `max-age=0` keeps the online behaviour identical to before (every request still goes to the
 * network, now as a conditional `If-None-Match` so unchanged data comes back as a tiny 304, which
 * also does not count against the API rate limit). Offline, [OfflineRequestInterceptor] asks for
 * stale entries explicitly via `max-stale`, so the very same entry is served from disk.
 *
 * This must be registered as a **network** interceptor: the OkHttp cache sits below application
 * interceptors, so a response rewritten at the application level would already have been stored
 * unchanged.
 */
class CacheResponseInterceptor(
    private val enabled: () -> Boolean = { OfflinePrefs.isEnabled },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (!enabled()) {
            // Offline caching switched off: make sure nothing new lands on disk. Entries written
            // earlier are dropped when the user clears the cache (settings screen).
            return response.newBuilder()
                .header("Cache-Control", "no-store")
                .removeHeader("Pragma")
                .build()
        }

        val request = chain.request()
        if (request.method != "GET" || !response.isSuccessful) return response
        if (response.header("Cache-Control").orEmpty().contains("no-store")) return response

        return response.newBuilder()
            .header("Cache-Control", "public, max-age=0")
            .removeHeader("Pragma")
            .build()
    }
}
