package io.github.busybin.gitrehub.provider.rest

import io.github.busybin.gitrehub.App
import io.github.busybin.gitrehub.BuildConfig
import io.github.busybin.gitrehub.provider.rest.interceptors.AuthenticationInterceptor
import io.github.busybin.gitrehub.provider.rest.interceptors.CacheResponseInterceptor
import io.github.busybin.gitrehub.provider.rest.interceptors.OfflineRequestInterceptor
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object HttpProvider {

    /**
     * Every client of the app is built here, so this is where the offline HTTP cache is attached.
     *
     * [CacheResponseInterceptor] is a **network** interceptor (the cache sits below the application
     * interceptors, so it has to see the raw response), while [OfflineRequestInterceptor] is added
     * last of all application interceptors so it observes the request with all headers in place —
     * required for the cache's `Vary: Authorization` matching to remain account-correct.
     */
    fun provideOkHttpClient(vararg interceptor: Interceptor): OkHttpClient {
        val client = OkHttpClient.Builder()
            .cache(HttpCache.get(App.getInstance()))
            .addNetworkInterceptor(CacheResponseInterceptor())
        if (BuildConfig.DEBUG) {
            client.addInterceptor(
                HttpLoggingInterceptor()
                    .setLevel(HttpLoggingInterceptor.Level.BODY)
            )
        }
        // The session's token is attached by default: forgetting this interceptor left every
        // rewritten call unauthenticated (public endpoints worked, /user and /user/repos 401).
        // An explicit interceptor (the token being validated) is added after it and wins.
        if (interceptor.none { it is AuthenticationInterceptor }) {
            client.addInterceptor(AuthenticationInterceptor())
        }
        for (inter in interceptor) {
            client.addInterceptor(inter)
        }
        client.addInterceptor(OfflineRequestInterceptor())
        return client.build()
    }

    /**
     * Client for large transfers (repository zipballs) and the parallel blob fallback.
     *
     * Deliberately **without** the disk cache: a 100 MB archive would evict every cached API answer
     * for one download, and zipballs are single-use anyway. Read timeout is generous because the
     * archive often comes from `codeload.github.com` on a slow phone connection.
     */
    fun provideDownloadClient(): OkHttpClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 8
            },
        )
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(AuthenticationInterceptor())
        .build()

    /**
     * Same as [provideOkHttpClient] but **never touches the network**: the interceptor that
     * normally only kicks in while offline is pinned to "offline".
     *
     * Used by the list screens to paint the previous visit straight from the disk cache before the
     * real request goes out (see `PagedViewModel.refresh`). When the cache has nothing, OkHttp
     * answers the synthetic `504 Unsatisfiable Request`, which callers treat as "no snapshot".
     */
    fun provideCacheOnlyClient(vararg interceptor: Interceptor): OkHttpClient {
        val client = OkHttpClient.Builder()
            .cache(HttpCache.get(App.getInstance()))
            .addNetworkInterceptor(CacheResponseInterceptor())
        for (inter in interceptor) {
            client.addInterceptor(inter)
        }
        client.addInterceptor(OfflineRequestInterceptor(isOnline = { false }))
        return client.build()
    }
}
