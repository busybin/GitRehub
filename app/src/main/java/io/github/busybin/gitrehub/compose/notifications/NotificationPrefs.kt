package io.github.busybin.gitrehub.compose.notifications

import android.content.Context
import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.helper.PrefGetter
import io.github.busybin.gitrehub.helper.PrefHelper
import io.github.busybin.gitrehub.provider.tasks.notification.NotificationForegroundService
import io.github.busybin.gitrehub.provider.tasks.notification.NotificationSchedulerJobTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Modern UI switches for "new message notifications".
 *
 * The work is still done by the legacy [NotificationSchedulerJobTask], which reads the old
 * `notificationEnabled` / `notificationTime` keys, so this writes those same keys.
 *
 * Android 13+ additionally needs the `POST_NOTIFICATIONS` runtime permission.
 */
object NotificationPrefs {

    private const val KEY_ENABLED = "notificationEnabled"
    private const val KEY_INTERVAL = "notificationTime"

    /** Tracks that the notification permission was already asked; only prompt once after login. */
    private const val KEY_PROMPTED = "m3_notifications_prompted"

    /** Foreground (per-second) service toggle. */
    private const val KEY_FOREGROUND = PrefGetter.NOTIFICATION_FOREGROUND

    private const val KEY_QUIET_ENABLED = "m3_quiet_hours_enabled"
    private const val KEY_QUIET_START = "m3_quiet_hours_start"
    private const val KEY_QUIET_END = "m3_quiet_hours_end"

    const val DEFAULT_QUIET_START = 22
    const val DEFAULT_QUIET_END = 7

    /** Interval options in minutes. A 1-minute value really means 1: the job reschedules itself
     *  instead of using `setPeriodic`, which the system clamps to 15 minutes. Doze may still delay it. */
    val intervals: List<Int> = listOf(1, 2, 5, 10, 15, 30, 60)

    const val DEFAULT_INTERVAL_MINUTES = 30

    private val _prompted = MutableStateFlow(
        PrefHelper.isExist<Boolean>(KEY_PROMPTED) && PrefHelper.getBoolean(KEY_PROMPTED)
    )

    /** Whether the "enable notifications?" prompt was already shown. */
    val prompted: StateFlow<Boolean> = _prompted.asStateFlow()

    fun markPrompted() {
        PrefHelper.putAny(KEY_PROMPTED, true)
        _prompted.value = true
    }

    private val _enabled = MutableStateFlow(
        PrefHelper.isExist<Boolean>(KEY_ENABLED) && PrefHelper.getBoolean(KEY_ENABLED)
    )
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _intervalMinutes = MutableStateFlow(
        PrefHelper.getString(KEY_INTERVAL)?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_INTERVAL_MINUTES
    )
    val intervalMinutes: StateFlow<Int> = _intervalMinutes.asStateFlow()

    /** Toggle; turning it off also clears any notifications already shown. */
    fun setEnabled(context: Context, enabled: Boolean) {
        PrefHelper.putAny(KEY_ENABLED, enabled)
        _enabled.value = enabled
        // The legacy scheduler reads this same key.
        NotificationSchedulerJobTask.scheduleJob(context)
        if (!enabled) {
            // Turning everything off also stops the foreground service.
            NotificationForegroundService.stop(context)
            runCatching {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
                manager.cancelAll()
            }
        }
    }

    fun setIntervalMinutes(context: Context, minutes: Int) {
        PrefHelper.putAny(KEY_INTERVAL, minutes.coerceIn(1, 24 * 60).toString())
        _intervalMinutes.value = minutes
        NotificationSchedulerJobTask.scheduleJob(context)
    }

    private val _foreground = MutableStateFlow(
        PrefHelper.isExist<Boolean>(KEY_FOREGROUND) && PrefHelper.getBoolean(KEY_FOREGROUND)
    )

    /** Foreground (per-second) service toggle. */
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    /**
     * Enable/disable the foreground service. Enabling cancels the legacy JobScheduler job so both
     * do not fire at once; disabling schedules it again.
     */
    fun setForegroundEnabled(context: Context, enabled: Boolean) {
        PrefHelper.putAny(KEY_FOREGROUND, enabled)
        _foreground.value = enabled
        // The service needs a foreground notification, so POST_NOTIFICATIONS is required (caller requests it).
        if (enabled) {
            NotificationForegroundService.start(context)
            NotificationSchedulerJobTask.cancelAll(context)
        } else {
            NotificationForegroundService.stop(context)
            NotificationSchedulerJobTask.scheduleJob(context)
        }
    }

    /** Called when the app returns to foreground to sync the service with the preference. */
    fun ensureService(context: Context) {
        if (_foreground.value) {
            NotificationForegroundService.start(context)
            // The job may have been re-armed by App.onCreate before this ran; only one poller may live.
            NotificationSchedulerJobTask.cancelAll(context)
        }
    }

    // ------------------------------------------------------------------ quiet hours

    private val _quietEnabled = MutableStateFlow(
        PrefHelper.isExist<Boolean>(KEY_QUIET_ENABLED) && PrefHelper.getBoolean(KEY_QUIET_ENABLED)
    )
    val quietEnabled: StateFlow<Boolean> = _quietEnabled.asStateFlow()

    private val _quietStart = MutableStateFlow(
        PrefHelper.getString(KEY_QUIET_START)?.toIntOrNull() ?: DEFAULT_QUIET_START
    )
    val quietStart: StateFlow<Int> = _quietStart.asStateFlow()

    private val _quietEnd = MutableStateFlow(
        PrefHelper.getString(KEY_QUIET_END)?.toIntOrNull() ?: DEFAULT_QUIET_END
    )
    val quietEnd: StateFlow<Int> = _quietEnd.asStateFlow()

    fun setQuietEnabled(enabled: Boolean) {
        PrefHelper.putAny(KEY_QUIET_ENABLED, enabled)
        _quietEnabled.value = enabled
    }

    fun setQuietStart(hour: Int) {
        val clean = hour.coerceIn(0, 23)
        PrefHelper.putAny(KEY_QUIET_START, clean.toString())
        _quietStart.value = clean
    }

    fun setQuietEnd(hour: Int) {
        val clean = hour.coerceIn(0, 23)
        PrefHelper.putAny(KEY_QUIET_END, clean.toString())
        _quietEnd.value = clean
    }

    /**
     * Whether notifications should be held right now. A window whose end is earlier than its start
     * wraps midnight (22 -> 07); start == end means "always quiet", which is what a user picking the
     * same hour twice means.
     */
    fun isQuietNow(hour: Int = java.time.LocalTime.now().hour): Boolean {
        if (!_quietEnabled.value) return false
        val start = _quietStart.value
        val end = _quietEnd.value
        return if (start == end) true
        else if (start < end) hour >= start && hour < end
        else hour >= start || hour < end
    }

    /** Label resource for a settings chip (follows the system language). */
    @StringRes
    fun labelRes(minutes: Int): Int = when {
        minutes == 60 -> R.string.m3_notif_every_1h
        minutes == 120 -> R.string.m3_notif_every_2h
        minutes == 180 -> R.string.m3_notif_every_3h
        else -> R.string.m3_notif_every_n_min
    }
}
