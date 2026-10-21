package io.github.busybin.gitrehub.helper

import android.content.res.Resources
import android.media.RingtoneManager
import android.net.Uri
import androidx.annotation.IntDef
import io.github.busybin.gitrehub.helper.InputHelper.isEmpty

/** Typed access to the preferences the legacy data layer still reads. */
object PrefGetter {
    const val LIGHT = 1
    const val DARK = 2

    const val DEFAULT_NOTIFICATION_MINUTES = 30
    private const val MAX_NOTIFICATION_MINUTES = 24 * 60

    private const val ENTERPRISE_TOKEN = "enterprise_token"
    private const val ENTERPRISE_OTP_CODE = "enterprise_otp_code"
    private const val ENTERPRISE_URL = "enterprise_url"
    private const val OTP_CODE = "otp_code"
    private const val APP_LANGUAGE = "app_language"
    private const val NOTIFICATION_SOUND_PATH = "notification_sound_path"
    private const val DISABLE_AUTO_LOAD_IMAGE = "disable_auto_loading_image"

    /** Shared with `NotificationPrefs`, which owns the toggle in the modern settings screen. */
    const val NOTIFICATION_FOREGROUND = "m3_notif_foreground"

    /**
     * In-memory mirror of the signed-in token, seeded by `core/auth/TokenStore` on launch. Kept
     * because the OkHttp interceptors are synchronous; nothing writes it to disk.
     */
    @JvmStatic
    @Volatile
    var token: String? = null

    @JvmStatic
    var otpCode: String?
        get() = PrefHelper.getString(OTP_CODE)
        set(value) {
            PrefHelper.putAny(OTP_CODE, value)
        }

    @JvmStatic
    val enterpriseToken: String?
        get() = PrefHelper.getString(ENTERPRISE_TOKEN)

    @JvmStatic
    var enterpriseOtpCode: String?
        get() = PrefHelper.getString(ENTERPRISE_OTP_CODE)
        set(value) {
            PrefHelper.putAny(ENTERPRISE_OTP_CODE, value)
        }

    @JvmStatic
    var enterpriseUrl: String?
        get() = PrefHelper.getString(ENTERPRISE_URL)
        set(value) {
            PrefHelper.putAny(ENTERPRISE_URL, value)
        }

    @JvmStatic
    val isEnterprise: Boolean
        get() = !isEmpty(enterpriseUrl)

    @JvmStatic
    fun clearCredentials() {
        token = null
        otpCode = null
        PrefHelper.clearKey<String>(OTP_CODE)
        PrefHelper.clearKey<String>(ENTERPRISE_TOKEN)
        PrefHelper.clearKey<String>(ENTERPRISE_OTP_CODE)
        PrefHelper.clearKey<String>(ENTERPRISE_URL)
    }

    fun getAppLanguage(resources: Resources): String =
        PrefHelper.getString(APP_LANGUAGE) ?: resources.configuration.locales[0].language

    /** The UI is dark only; kept for the few call sites that ask for a theme. */
    @JvmStatic
    @ThemeType
    fun getThemeType(resources: Resources): Int = DARK

    val isAutoImageDisabled: Boolean
        get() = PrefHelper.getBoolean(DISABLE_AUTO_LOAD_IMAGE) && AppHelper.isDataPlan

    val isMarkAsReadEnabled: Boolean
        get() = PrefHelper.getBoolean("markNotificationAsRead")

    val isNotificationSoundEnabled: Boolean
        get() = PrefHelper.getBoolean("notificationSound")

    val notificationSound: Uri?
        get() {
            val path = PrefHelper.getString(NOTIFICATION_SOUND_PATH)
            return if (isEmpty(path)) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } else {
                Uri.parse(path)
            }
        }

    /** Minutes between checks, or -1 when the background check is off. */
    val notificationIntervalMinutes: Int
        get() {
            if (!PrefHelper.getBoolean("notificationEnabled")) return -1
            val minutes = PrefHelper.getString("notificationTime")?.trim()?.toIntOrNull()
                ?: return DEFAULT_NOTIFICATION_MINUTES
            return if (minutes in 1..MAX_NOTIFICATION_MINUTES) minutes else DEFAULT_NOTIFICATION_MINUTES
        }

    /** The 15-second foreground poller; while it runs the interval job must not be scheduled too. */
    val isNotificationForegroundEnabled: Boolean
        get() = PrefHelper.getBoolean(NOTIFICATION_FOREGROUND)

    @IntDef(LIGHT, DARK)
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class ThemeType
}
