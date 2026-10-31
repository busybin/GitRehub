package io.github.busybin.gitrehub.compose.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Only load images on Wi-Fi": for metered mobile data, GitHub avatars and README screenshots add up
 * quickly. Off by default; when on, the image client answers 504 for pictures while on mobile data,
 * so Coil shows its placeholder and the text still loads.
 */
object NetworkPrefs {

    private const val KEY_WIFI_ONLY_IMAGES = "m3_wifi_only_images"

    private val _wifiOnlyImages = MutableStateFlow(
        PrefHelper.isExist<Boolean>(KEY_WIFI_ONLY_IMAGES) && PrefHelper.getBoolean(KEY_WIFI_ONLY_IMAGES)
    )
    val wifiOnlyImages: StateFlow<Boolean> = _wifiOnlyImages.asStateFlow()

    fun setWifiOnlyImages(enabled: Boolean) {
        PrefHelper.putAny(KEY_WIFI_ONLY_IMAGES, enabled)
        _wifiOnlyImages.value = enabled
    }

    /** True when the default network is metered (mobile data). */
    fun isMetered(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun shouldSkipImages(context: Context): Boolean =
        _wifiOnlyImages.value && isMetered(context)
}
