package io.github.busybin.gitrehub

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import io.github.busybin.gitrehub.compose.common.AppShortcuts
import io.github.busybin.gitrehub.core.auth.TokenStore
import io.github.busybin.gitrehub.helper.OfflineStatus
import io.github.busybin.gitrehub.helper.PrefHelper.init
import io.github.busybin.gitrehub.helper.TypeFaceHelper.generateTypeface
import io.github.busybin.gitrehub.provider.colors.ColorsProvider
import io.github.busybin.gitrehub.provider.rest.interceptors.ImageAuthInterceptor
import io.github.busybin.gitrehub.provider.crash.Report
import io.github.busybin.gitrehub.provider.emoji.EmojiStore
import io.github.busybin.gitrehub.provider.tasks.notification.NotificationSchedulerJobTask
import okhttp3.OkHttpClient

/**
 * Created by Kosh on 03 Feb 2017, 12:07 AM
 */
class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        instance = this
        init()
    }

    /**
     * The one image pipeline for the whole app (avatars, screenshots in READMEs, image files).
     *
     * Two things differ from Coil's defaults, both because of the "avatars re-download every single
     * time" complaint:
     *  - [ImageLoader.Builder.respectCacheHeaders] is off. `avatars.githubusercontent.com` serves
     *    `max-age=300`, so with the default the memory *and* disk entries expire after five minutes
     *    and every screen re-downloads the same picture. We can't tune their CDN, so we keep it
     *    until it is evicted instead.
     *  - an explicit 128 MB disk cache: without one the pictures are gone as soon as the system
     *    decides to clear the (unbounded-in-name-only) default cache directory.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .respectCacheHeaders(false)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image-cache"))
                .maxSizeBytes(128L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        // Animated GIFs in READMEs and issue bodies (coil-gif).
        .components { add(GifDecoder.Factory()) }
        // README images of private repositories need the token (see ImageAuthInterceptor).
        .okHttpClient { OkHttpClient.Builder().addInterceptor(ImageAuthInterceptor()).build() }
        .build()

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun initShortcut() {
        AppShortcuts.publish(this)
    }

    private fun init() {
        Report.init(applicationContext)
        deleteDatabase("database.db")
        setupPreference()
        // Accounts/tokens live in an encrypted file; this also seeds the in-memory token the
        // OkHttp interceptors read.
        TokenStore.init(applicationContext)
        // Offline cache uses this to pick cache-first vs network-first.
        OfflineStatus.init(this)
        generateTypeface(this)
        NotificationSchedulerJobTask.scheduleJob(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            // A binder round trip; keep it off the startup thread.
            Thread { initShortcut() }.start()
        }
        EmojiStore.load(this)
        ColorsProvider.load()
    }

    private fun setupPreference() {
        init(this.applicationContext)
    }

    companion object {
        private lateinit var instance: App

        @JvmStatic
        fun getInstance(): App {
            return instance
        }
    }
}
