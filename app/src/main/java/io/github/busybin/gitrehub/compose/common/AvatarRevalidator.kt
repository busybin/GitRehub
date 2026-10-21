package io.github.busybin.gitrehub.compose.common

import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Detects an avatar picture that changed behind an unchanged URL.
 *
 * GitHub serves avatars from `avatars.githubusercontent.com/u/<id>?v=4`: replacing the picture does
 * not change the URL, so the image cache would keep the old bytes forever. The ETag does change, so
 * a conditional GET tells us when to refetch — a `304 Not Modified` costs a few hundred bytes.
 */
internal class AvatarRevalidator(
    private val client: OkHttpClient,
    private val etagOf: (String) -> String?,
    private val rememberEtag: (String, String) -> Unit,
) {

    /** @return true when the picture behind [url] is different from the last one we saw. */
    fun changed(url: String): Boolean {
        val known = etagOf(url)
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .apply { if (!known.isNullOrBlank()) header("If-None-Match", known) }
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val etag = response.header("ETag")
            if (etag.isNullOrBlank()) return@use false
            if (etag != known) rememberEtag(url, etag)
            !known.isNullOrBlank() && etag != known
        }
    }
}
