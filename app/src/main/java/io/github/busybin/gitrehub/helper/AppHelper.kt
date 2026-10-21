package io.github.busybin.gitrehub.helper

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import io.github.busybin.gitrehub.App
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.helper.PrefGetter.getThemeType

object AppHelper {
    @JvmStatic
    @JvmOverloads
    fun cancelNotification(context: Context, id: Int = BundleConstant.REQUEST_CODE) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
    }

    fun cancelAllNotifications(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }

    @JvmStatic
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
        Toast.makeText(App.getInstance(), R.string.success_copied, Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun isNightMode(resources: Resources): Boolean = getThemeType(resources) != PrefGetter.LIGHT

    /** True on mobile data, where automatic image loading is skipped when the user opted out. */
    val isDataPlan: Boolean
        @SuppressLint("MissingPermission")
        get() {
            val manager = App.getInstance()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
}
