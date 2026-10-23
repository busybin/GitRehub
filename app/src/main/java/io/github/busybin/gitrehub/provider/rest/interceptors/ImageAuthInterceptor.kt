package io.github.busybin.gitrehub.provider.rest.interceptors

import io.github.busybin.gitrehub.helper.PrefGetter
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the token to image requests that GitHub serves per-repository.
 *
 * README images of a **private** repository live behind the same permission check as the
 * repository itself: `raw.githubusercontent.com/...` answers 404 to an anonymous request, so the
 * image slot stayed empty until the repository was public. The API host is not in the list — those
 * calls go through [AuthenticationInterceptor] already.
 */
class ImageAuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = PrefGetter.token?.takeIf { it.isNotBlank() } ?: return chain.proceed(request)
        if (request.header("Authorization") != null) return chain.proceed(request)
        if (request.url.host !in PRIVATE_IMAGE_HOSTS) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", "token $token").build())
    }

    private companion object {
        /** Raw file bytes (`raw.githubusercontent.com`) and attachments pasted into issues. */
        val PRIVATE_IMAGE_HOSTS = setOf("raw.githubusercontent.com", "user-images.githubusercontent.com")
    }
}
