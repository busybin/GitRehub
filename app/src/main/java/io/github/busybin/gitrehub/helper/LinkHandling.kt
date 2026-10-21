package io.github.busybin.gitrehub.helper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Let GitRehub open github.com links" — the one thing the user has to do by hand.
 *
 * Android only hands a web domain to an app automatically when that domain hosts an
 * `assetlinks.json` naming the app. github.com's file belongs to GitHub's own clients, so an app
 * like ours is never verified and always loses to the browser. The user has to flip the switch in
 * Settings → Apps → GitRehub → Open by default → Open supported links.
 *
 * It used to live only in the settings screen, which meant nobody found it (their complaint). Now
 * the app offers it right after login, and the settings entry still works.
 */
object LinkHandling {

    /** Jump to this app's "open supported links" page, falling back to the app details page. */
    fun openSettings(context: Context) {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
        // Some ROMs have no such page; fall back to the app details screen.
        runCatching { context.startActivity(direct) }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
            }
        }
    }

    /**
     * Whether the system would actually hand us a github.com link right now.
     *
     * The probe uses a repository URL (a shape we do have a screen for) and asks which app wins;
     * once the user has flipped the switch this comes back as *us*, before that as the browser.
     */
    fun isHandling(context: Context): Boolean = runCatching {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/busybin/gitrehub"))
        context.packageManager
            .resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)
}

/**
 * Remembers that the offer was already made, so the dialog does not come back on every launch.
 * Answering it either way counts (if the user backed out of Settings, the settings entry is still
 * there — nagging every start would be worse than the thing it is trying to fix).
 */
object LinkHandlingPrefs {

    private const val KEY_ASKED = "m3_link_handling_asked"

    private val _asked = MutableStateFlow(readAsked())
    val asked: StateFlow<Boolean> = _asked.asStateFlow()

    fun markAsked() {
        PrefHelper.putAny(KEY_ASKED, true)
        _asked.value = true
    }

    private fun readAsked(): Boolean = runCatching {
        PrefHelper.isExist<Boolean>(KEY_ASKED) && PrefHelper.getBoolean(KEY_ASKED)
    }.getOrDefault(false)
}
