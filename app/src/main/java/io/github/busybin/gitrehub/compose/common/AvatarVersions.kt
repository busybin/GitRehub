package io.github.busybin.gitrehub.compose.common

import android.content.Context
import io.github.busybin.gitrehub.App
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.helper.OfflineStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache-busting versions for avatars.
 *
 * GitHub keeps the avatar URL when the picture is replaced, and `App.newImageLoader` deliberately
 * ignores cache headers, so Coil would show the first copy forever. This tracks a per-URL revision:
 * bump it and [Avatar] asks Coil for the same URL under a new cache key, which forces a refetch.
 * Revisions are only bumped after an ETag check confirms the picture really changed, so the cache
 * stays stable (and usable offline) the rest of the time.
 */
object AvatarVersions {

    private const val CHECK_INTERVAL_MS = 10 * 60_000L
    private const val FORCE_INTERVAL_MS = 60_000L
    private const val MAX_SEEN = 120
    private const val PREFS = "m3_avatar_etags"

    private val _revisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val revisions: StateFlow<Map<String, Long>> = _revisions.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val lastChecked = ConcurrentHashMap<String, Long>()
    private val lastForced = ConcurrentHashMap<String, Long>()
    private val seen = LinkedHashSet<String>()
    private val client by lazy { OkHttpClient.Builder().build() }
    private val prefs by lazy {
        App.getInstance().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    private val revalidator by lazy {
        AvatarRevalidator(
            client = client,
            etagOf = { prefs.getString(it, null) },
            rememberEtag = { url, etag -> prefs.edit().putString(url, etag).apply() },
        )
    }

    /**
     * Checks whether the picture behind [url] changed, at most once per [CHECK_INTERVAL_MS] per URL
     * ([FORCE_INTERVAL_MS] when [force]). Safe to call from composition — it only schedules work.
     */
    fun check(url: String?, force: Boolean = false) {
        if (url.isNullOrBlank()) return
        remember(url)
        val now = System.currentTimeMillis()
        if (force) {
            val previous = lastForced[url]
            if (previous != null && now - previous < FORCE_INTERVAL_MS) return
            lastForced[url] = now
        } else {
            val previous = lastChecked[url]
            if (previous != null && now - previous < CHECK_INTERVAL_MS) return
        }
        lastChecked[url] = now
        if (!running.add(url)) return
        scope.launch {
            try {
                if (!OfflineStatus.isOnline) {
                    lastChecked.remove(url)
                    return@launch
                }
                if (revalidator.changed(url)) bump(url)
            } catch (_: Exception) {
                // Network problem: keep the cached picture, the next pass retries.
            } finally {
                running.remove(url)
            }
        }
    }

    /** Re-checks the signed-in user's avatar; used when the app comes back to the foreground. */
    fun checkOwn() {
        scope.launch {
            Session.currentAccount()?.avatarUrl?.let { check(it, force = true) }
        }
    }

    /**
     * Re-checks every avatar shown recently. Screens stay composed while the app is in the
     * background, so without this their pictures would only be re-checked after scrolling.
     */
    fun checkSeen() {
        val urls = synchronized(seen) { seen.toList() }
        scope.launch { urls.forEach { check(it) } }
    }

    private fun remember(url: String) {
        synchronized(seen) {
            seen.remove(url)
            seen.add(url)
            while (seen.size > MAX_SEEN) {
                val oldest = seen.first()
                seen.remove(oldest)
                lastChecked.remove(oldest)
                lastForced.remove(oldest)
            }
        }
    }

    private fun bump(url: String) {
        _revisions.update { current ->
            current + (url to (current[url] ?: 0L) + 1L)
        }
    }
}
