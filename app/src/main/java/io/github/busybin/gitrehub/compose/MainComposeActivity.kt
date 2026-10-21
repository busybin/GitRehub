package io.github.busybin.gitrehub.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.busybin.gitrehub.compose.common.LanguagePrefs
import io.github.busybin.gitrehub.helper.OfflineStatus
import io.github.busybin.gitrehub.provider.scheme.SchemeParser

/**
 * Host of the modern (Compose) UI, and the only launcher activity.
 *
 * It also carries a route in from outside: [DeepLinkActivity] (and anything else that already
 * resolved a link to a screen) puts one in [SchemeParser.EXTRA_NAVIGATE]. `singleTop` means a link
 * arriving while the app is up is delivered to [onNewIntent] rather than a second copy of this
 * activity.
 */
class MainComposeActivity : ComponentActivity() {

    private val routeRequest: MutableState<String?> = mutableStateOf(null)

    /** In-app language (from settings); restartModernUi hands the new Locale to the next Activity. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        routeRequest.value = intent?.getStringExtra(SchemeParser.EXTRA_NAVIGATE)
        setContent { FaApp(routeRequest) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeRequest.value = intent.getStringExtra(SchemeParser.EXTRA_NAVIGATE)
    }

    /**
     * Coming back from the lock screen is exactly when the "offline" banner used to get stuck: the
     * callbacks that end an offline spell can be missed while the process is suspended, so the
     * online/offline state is re-read here.
     */
    override fun onResume() {
        super.onResume()
        OfflineStatus.refresh()
    }
}
