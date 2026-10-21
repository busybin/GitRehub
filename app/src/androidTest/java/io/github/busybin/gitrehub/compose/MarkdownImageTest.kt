package io.github.busybin.gitrehub.compose

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.compose.common.HtmlText
import io.github.busybin.gitrehub.compose.theme.FaTheme
import io.github.busybin.gitrehub.provider.timeline.handler.drawable.UrlDrawable
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The legacy pipeline loads `<img>` through Glide and paints it as an `ImageSpan` inside a
 * `TextView` that Compose hosts via `AndroidView`. "Glide can fetch a URL" (see
 * [ImageLoadingTest]) does not prove "a README image shows up", so this walks the real view tree
 * after the async load and looks for a laid-out image.
 */
@RunWith(AndroidJUnit4::class)
class MarkdownImageTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val url = "https://avatars.githubusercontent.com/u/1?v=4"

    /** Relative on purpose: this is the shape README screenshots actually come in. */
    private val relative = "screenshots/home.jpg"
    private val base = "https://raw.githubusercontent.com/busybin/DelugeLauncher/HEAD/"

    @Test
    fun markdownImageEndsUpWithNonZeroBounds() {
        rule.setContent {
            FaTheme {
                HtmlText(html = "<p>hello</p><img src=\"$url\"><p>bye</p>")
            }
        }
        rule.waitForIdle()

        var found = false
        rule.waitUntil(timeoutMillis = 20_000) {
            found = rule.runOnUiThread {
                textViews(rule.activity.window.decorView).any { tv -> hasImageSpan(tv) }
            }
            found
        }
        assertTrue("no ImageSpan with a decoded drawable was ever laid out", found)
    }

    @Test
    fun relativeImagePathIsResolvedAgainstTheBaseUrl() {
        rule.setContent {
            FaTheme {
                HtmlText(html = "<p>hello</p><img src=\"$relative\">", baseUrl = base)
            }
        }
        rule.waitForIdle()

        var found = false
        rule.waitUntil(timeoutMillis = 20_000) {
            found = rule.runOnUiThread {
                textViews(rule.activity.window.decorView).any { tv -> hasImageSpan(tv) }
            }
            found
        }
        assertTrue("relative README image never resolved/loaded", found)
    }

    @Test
    fun anImageThatCannotLoadStillReservesSpace() {
        // 127.0.0.1:1 has no listener, so the connection fails immediately without DNS: the image
        // cannot load. (Not *.invalid: some resolvers answer every domain, so the image "loads".)
        rule.setContent {
            FaTheme {
                HtmlText(html = "<p>before</p><img src=\"http://127.0.0.1:1/x.png\"><p>after</p>")
            }
        }
        rule.waitForIdle()

        var report = "(never inspected)"
        // Wait for Glide to finish (success or failure), then check the span still reserves space.
        runCatching {
            rule.waitUntil(timeoutMillis = 30_000) {
                report = rule.runOnUiThread { describeReservation() }
                report.startsWith("OK")
            }
        }
        assertTrue("image placeholder did not reserve space: $report", report.startsWith("OK"))
    }

    /** `OK <state>`, or a diagnostic string so a failure shows what the tree actually looks like. */
    private fun describeReservation(): String {
        val spanned = textViews(rule.activity.window.decorView)
            .mapNotNull { it.text as? android.text.Spanned }
        if (spanned.isEmpty()) return "no Spanned TextView in the tree"
        val spans = spanned.flatMap { text ->
            text.getSpans(0, text.length, android.text.style.ImageSpan::class.java).toList()
        }
        if (spans.isEmpty()) return "no ImageSpan at all"
        val wrappers = spans.map { span ->
            val w = span.drawable
            Triple(w?.javaClass?.simpleName, w?.bounds?.toShortString(), loadedDrawable(w) != null)
        }
        val pending = wrappers.filter { !it.third }
        val reserved = pending.count { it.second != null && !it.second!!.startsWith("0, 0 - 0, 0") }
        val shape = "spans=${spans.size} pending=${pending.size} reserved=$reserved wrappers=$wrappers"
        return if (pending.isNotEmpty() && reserved == pending.size) "OK $shape" else "BAD $shape"
    }

    private fun hasImageSpan(tv: TextView): Boolean {
        val text = tv.text ?: return false
        if (text !is android.text.Spanned) return false
        return text.getSpans(0, text.length, android.text.style.ImageSpan::class.java)
            .any { span -> loadedDrawable(span.drawable) != null }
    }

    /**
     * The **decoded** drawable behind the span, or null while the picture is still loading.
     *
     * Bounds alone prove nothing: since the loading placeholder was added, the wrapper has real
     * bounds from the moment it is created.
     */
    private fun loadedDrawable(wrapper: android.graphics.drawable.Drawable?): android.graphics.drawable.Drawable? =
        (wrapper as? UrlDrawable)?.image

    private fun textViews(root: View): List<TextView> {
        val out = mutableListOf<TextView>()
        fun walk(v: View) {
            if (v is TextView) out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return out
    }
}
