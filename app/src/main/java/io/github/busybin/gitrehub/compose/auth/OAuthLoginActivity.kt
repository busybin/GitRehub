package io.github.busybin.gitrehub.compose.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import io.github.busybin.gitrehub.BuildConfig
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LanguagePrefs
import io.github.busybin.gitrehub.core.auth.OAuthFlow
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.helper.BrowserLauncher
import io.github.busybin.gitrehub.helper.GithubConfigHelper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * The GitHub OAuth round trip, and nothing else. No UI of its own.
 *
 * This is all that survived of the classic `LoginActivity`: the token-paste form, the two-factor
 * fields and the enterprise endpoint field were all replaced by the Compose login screen, but the
 * browser hand-off still needs an activity to come back to — `gitrehub://login?code=…` is declared
 * in the manifest and GitHub redirects there.
 *
 * Launched plainly it opens the authorization page; launched by the redirect it exchanges the code
 * for a token, stores it and finishes. The Compose app re-checks the login state on resume, so
 * there is nothing to hand back.
 */
class OAuthLoginActivity : ComponentActivity() {

    private val scope = MainScope()

    /** The redirect is delivered twice on some devices (onNewIntent *and* onResume). */
    private var exchanging = false

    /** In-app language (see [LanguagePrefs]) - toasts must follow it too. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!handleRedirect(intent)) openAuthorization()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRedirect(intent)
    }

    override fun onResume() {
        super.onResume()
        // The custom tab hands the redirect back as a new intent on most builds, but on some it is
        // only visible here. `exchanging` keeps the code from being spent twice.
        handleRedirect(intent)
    }

    /** @return true when this intent was the redirect (handled or already being handled). */
    private fun handleRedirect(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (!data.toString().startsWith(GithubConfigHelper.redirectUrl)) return false
        if (exchanging) return true
        exchanging = true

        val code = data.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            toast(str(R.string.m3_oauth_no_code))
            finish()
            return true
        }
        exchange(code)
        return true
    }

    private fun openAuthorization() {
        val url = Uri.Builder().scheme("https")
            .authority("github.com")
            .appendPath("login")
            .appendPath("oauth")
            .appendPath("authorize")
            .appendQueryParameter("client_id", GithubConfigHelper.clientId)
            .appendQueryParameter("redirect_uri", GithubConfigHelper.redirectUrl)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("state", BuildConfig.GITHUB_APP_ID)
            .build()
        // A plain VIEW, not a Custom Tab: the OAuth page must open in the user's own browser, and
        // the Custom-Tabs helper used to force Chrome (or hand-build a chooser) instead.
        BrowserLauncher.open(this, url.toString())
    }

    private fun exchange(code: String) {
        scope.launch {
            var message = str(R.string.m3_login_failed)
            runCatching {
                val token = OAuthFlow.exchangeCode(code)
                // Validates the token against GET /user before storing it, as token login does.
                Session.loginWithToken(token)
            }.onSuccess {
                message = str(R.string.m3_oauth_success)
            }.onFailure {
                message = str(R.string.m3_oauth_failed, it.message.orEmpty())
            }
            toast(message)
            finish()
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    /** Every string comes from resources so toasts follow the app language too. */
    private fun str(@StringRes id: Int, vararg args: Any): String = getString(id, *args)

    private companion object {
        const val SCOPE = "user,repo,gist,notifications,read:org,workflow,read:packages"
    }
}
