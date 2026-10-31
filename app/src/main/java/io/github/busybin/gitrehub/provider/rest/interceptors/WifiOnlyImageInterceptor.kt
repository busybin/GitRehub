package io.github.busybin.gitrehub.provider.rest.interceptors

import android.content.Context
import io.github.busybin.gitrehub.compose.common.NetworkPrefs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Answers image requests with a synthetic 504 while "only load images on Wi-Fi" is on and the phone
 * is on mobile data. OkHttp-level so every image path (avatars, README pictures, image files) obeys
 * it without touching each call site; Coil renders its placeholder for a failed request.
 */
class WifiOnlyImageInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!NetworkPrefs.shouldSkipImages(context)) return chain.proceed(chain.request())
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(504)
            .message("Images are limited to Wi-Fi")
            .body("".toResponseBody("text/plain".toMediaType()))
            .build()
    }
}
