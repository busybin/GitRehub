package io.github.busybin.gitrehub.compose

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.github.busybin.gitrehub.helper.BrowserLauncher
import io.github.busybin.gitrehub.helper.InputHelper.isEmpty
import io.github.busybin.gitrehub.provider.scheme.SchemeParser

/**
 * Entry point for links and shares from outside the app — declared in the manifest for
 * `github.com`, `gist.github.com`, `raw.githubusercontent.com`, and for `ACTION_SEND` of plain
 * text. No UI: it works out which screen the link means and hands the Compose app a route.
 *
 * This replaces the classic `LinksParserActivity`, which did the same thing by building an Intent
 * per legacy activity.
 */
class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (val action = intent?.action) {
            Intent.ACTION_SEND -> {
                val shared = intent?.getStringExtra(Intent.EXTRA_TEXT)
                // Shared text is usually "some words https://github.com/owner/repo more words".
                val url = shared?.let { firstUrlIn(it) }
                if (url != null) open(Uri.parse(url)) else finishQuietly()
            }
            Intent.ACTION_VIEW -> {
                val data = intent?.data
                if (data != null) open(data) else finishQuietly()
            }
            else -> {
                finishQuietly()
            }
        }
    }

    private fun open(uri: Uri) {
        val route = SchemeParser.routeFor(uri)
        if (route == null) {
            // Nothing native to show it in — the browser, not a dead end. BrowserLauncher pins the
            // intent to the default browser, which also keeps this from resolving back to ourselves
            // (we declare github.com filters too) and looping.
            BrowserLauncher.open(this, uri)
        } else {
            startActivity(
                Intent(this, MainComposeActivity::class.java)
                    .putExtra(SchemeParser.EXTRA_NAVIGATE, route)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        finish()
    }

    private fun finishQuietly() = finish()

    private fun firstUrlIn(text: String): String? =
        text.split(Regex("\\s+")).firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            ?.trimEnd('.', ',', ')', ']', '"', '\'')
            ?.takeIf { !isEmpty(it) }
}
