package io.github.busybin.gitrehub.compose

import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.compose.common.HtmlText
import io.github.busybin.gitrehub.compose.common.repoRawBaseUrl
import io.github.busybin.gitrehub.compose.theme.FaTheme
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Real-README regression using `busybin/DelugeLauncher`'s server-rendered README HTML.
 *
 * It takes the real one because its screenshots are relative-path images in 4-column tables
 * (`<markdown-accessiblity-table><table>…<td><a><img src="screenshots/home.png">…`), a shape
 * hand-made fixtures never reproduce. Asserts every image the HTML carries ends up with a usable
 * size; the count is read from that HTML so adding a screenshot row does not break the test.
 */
@RunWith(AndroidJUnit4::class)
class RealReadmeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val owner = "busybin"
    private val repo = "DelugeLauncher"
    private val branch = "main"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** GitHub's server-rendered README (same HTML shape `RepoDetailScreen` receives). */
    private fun readmeHtml(): String? = runCatching {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/readme")
            .header("Accept", "application/vnd.github.html+json")
            .header("User-Agent", "GitRehub-test")
            .build()
        client.newCall(request).execute().use { response ->
            response.body?.string()?.takeIf { response.isSuccessful && it.contains("<img") }
        }
    }.getOrNull()

    @Test
    fun theDelugeReadmeShowsItsFourScreenshots() {
        val html = readmeHtml()
        assumeTrue("could not fetch the README (network / rate limit), skipping", html != null)
        val expectedImages = html?.split("<img")?.size?.minus(1) ?: 0

        rule.setContent {
            FaTheme {
                HtmlText(html = html, baseUrl = repoRawBaseUrl(owner, repo, branch))
            }
        }
        rule.waitForIdle()

        val report = java.util.concurrent.atomic.AtomicReference("(nothing)")
        runCatching {
            rule.waitUntil(timeoutMillis = 40_000) {
                val r = rule.runOnUiThread { inspect(expectedImages) }
                report.set(r)
                r.startsWith("OK")
            }
        }
        val text = report.get()
        assertTrue("DelugeLauncher README did not render correctly: $text", text.startsWith("OK"))
    }

    private fun inspect(expectedImages: Int): String {
        // The first TextView in the hierarchy is not always the README (toolbars/placeholders are
        // TextViews too): pick the one that actually carries the image spans.
        val tv = textViews(rule.activity.window.decorView)
            .filter { it.text is Spanned && (it.text as Spanned).getSpans(0, it.text.length, ImageSpan::class.java).isNotEmpty() }
            .maxByOrNull { it.text.length }
            ?: return "no TextView"
        val text = tv.text as? Spanned ?: return "not Spanned"
        val images = text.getSpans(0, text.length, ImageSpan::class.java)
            .sortedBy { text.getSpanStart(it) }
        val widths = images.map { span ->
            val d = span.drawable ?: return@map 0
            if (d.bounds.width() > 0) d.bounds.width() else d.intrinsicWidth
        }
        val loaded = widths.count { it > 0 }

        // The table section reads `Home | App list | ...` followed by one line of four images per
        // row (the README's four-column screenshot table must stay horizontal).
        val flat = text.toString().replace('\uFFFC', '#').replace('\n', '⏎')
        val tableAt = flat.indexOf("Long-press menu")
        val table = if (tableAt >= 0) flat.substring(tableAt, minOf(flat.length, tableAt + 160)) else "(table not found)"
        val bad = mutableListOf<String>()
        if (images.size != expectedImages) bad += "found ${images.size} images (the README has $expectedImages)"
        if (loaded < expectedImages) bad += "only $loaded have a size"
        // A 4-column image table keeps its images side by side, one column per screenshot.
        if (!flat.contains("# | # | # | #")) bad += "the four table images are not on one row"
        // Images with no separator at all means they were crammed into a single span-less line.
        if (flat.contains("##")) bad += "images are crammed without separators"
        if (images.any { text.getSpanStart(it) < 0 }) bad += "an ImageSpan starts outside the text"
        // Table images are capped at one column; standalone ones (the README's coffee badge) only at
        // the view width. The table begins at its first header cell.
        val headerAt = text.toString().indexOf("App freezing")
        if (headerAt < 0) bad += "table header not found"
        val tableStart = images.indexOfFirst { text.getSpanStart(it) > headerAt }
            .takeIf { it >= 0 } ?: images.size
        val inTable = widths.drop(tableStart)
        if (headerAt >= 0 && inTable.isEmpty()) bad += "no table images"
        if (inTable.any { it > tv.width / 4 + 2 }) bad += "an image is wider than one column: $inTable"
        if (widths.take(tableStart).any { it > tv.width }) {
            bad += "a standalone image is wider than the view: ${widths.take(tableStart)}"
        }

        val shape = "tvW=${tv.width} loaded=$loaded/$loaded widths=$widths table=[$table]"
        return if (bad.isEmpty()) "OK $shape" else "BAD ${bad.joinToString("; ")} :: $shape"
    }

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
