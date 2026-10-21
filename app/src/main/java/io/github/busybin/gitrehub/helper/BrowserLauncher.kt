package io.github.busybin.gitrehub.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Hands a URL to the browser: the user's default browser when there is one, otherwise the system
 * chooser (listing browsers, not this app).
 *
 * ## Why the default is resolved by hand (2026-10-20)
 * A bare `ACTION_VIEW` at a `github.com` URL has two honest handlers: the browser and this app's
 * own link handler. When the user has a default browser but never chose one *for app links*, the
 * system answers with a chooser — so "open in browser" showed a picker instead of the browser that
 * is already the default for every other app. Resolving the default browser first and pinning the
 * package avoids that, and the chooser is only built when no default exists.
 *
 * ## Why not the old hand-built chooser (2026-10-17)
 * It filtered by package visibility (Android 11+), so a Firefox fork without Custom Tabs support
 * could be missing from the list. The chooser built here comes from the system, which is not
 * subject to our visibility rules; this app's own link handler is excluded explicitly, because
 * "open in browser" must not bounce back into the app.
 */
object BrowserLauncher {

    /** @return true when something was launched; false when the caller must offer the URL itself. */
    fun open(context: Context, url: String): Boolean = open(context, Uri.parse(url))

    fun open(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (uri.scheme != "http" && uri.scheme != "https") {
            return runCatching { context.startActivity(intent) }.isSuccess
        }
        val browser = defaultBrowser(context)
        if (browser != null) {
            return runCatching { context.startActivity(Intent(intent).setPackage(browser)) }
                .isSuccess || runCatching { context.startActivity(chooser(context, intent)) }
                .isSuccess
        }
        return runCatching { context.startActivity(chooser(context, intent)) }.isSuccess
    }

    /**
     * The browser the system would use for a plain web link, or null when nothing is default.
     *
     * The probe uses a host this app has no filter for, so it never resolves back to us; a package
     * that only *asks* (the resolver) cannot be launched by name either, so it is not a default.
     */
    private fun defaultBrowser(context: Context): String? = runCatching {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com/"))
        val resolved = context.packageManager
            .resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            ?: return@runCatching null
        if (resolved == context.packageName) return@runCatching null
        // Only a package that really handles web links counts: with no default set, some ROMs
        // answer with their own resolver activity, which cannot be launched by name.
        context.packageManager.queryIntentActivities(probe, 0)
            .any { it.activityInfo.packageName == resolved }
            .let { if (it) resolved else null }
    }.getOrNull()

    /** The system chooser for [intent], without this app's own link handler. */
    private fun chooser(context: Context, intent: Intent): Intent {
        val chooser = Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val self = runCatching {
            context.packageManager.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName == context.packageName }
                .map { ComponentName(context.packageName, it.activityInfo.name) }
                .toTypedArray()
        }.getOrNull()
        if (!self.isNullOrEmpty()) chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, self)
        return chooser
    }
}
