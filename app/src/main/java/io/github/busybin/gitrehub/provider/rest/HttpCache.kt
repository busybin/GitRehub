package io.github.busybin.gitrehub.provider.rest

import android.content.Context
import okhttp3.Cache
import java.io.File

/**
 * The disk cache every OkHttp client of this app shares.
 *
 * One instance for the whole process, so the settings screen can report its size / evict it even
 * though the client itself is built (and re-built on logout) elsewhere.
 *
 * `context.cacheDir` is on the app's private storage: cached API responses (which can contain
 * private repository data) are not readable by other apps, and the system may reclaim the space
 * when storage runs low.
 */
object HttpCache {

    private const val DIR_NAME = "gitrehub-http"
    private const val MAX_BYTES = 64L * 1024 * 1024

    @Volatile
    private var cache: Cache? = null

    /** Creates the cache on first use. [context] is only needed the first time. */
    fun get(context: Context): Cache =
        cache ?: synchronized(this) {
            cache ?: Cache(File(context.applicationContext.cacheDir, DIR_NAME), MAX_BYTES)
                .also { cache = it }
        }

    /** Bytes currently on disk (0 when the cache was never built). */
    fun sizeBytes(): Long = runCatching { cache?.size() ?: 0L }.getOrDefault(0L)

    fun clear() {
        runCatching { cache?.evictAll() }
    }
}
