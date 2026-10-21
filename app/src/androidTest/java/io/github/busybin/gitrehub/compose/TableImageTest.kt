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
import io.github.busybin.gitrehub.compose.theme.FaTheme
import io.github.busybin.gitrehub.provider.timeline.handler.ImageClickSpan
import io.github.busybin.gitrehub.provider.timeline.handler.drawable.UrlDrawable
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * README **table images**. The table mirrors GitHub's real rendered shape (`<thead>`/`<tbody>`, two
 * columns, images wrapped in `<a>`) plus a text-only row after the image row — that row is what breaks
 * the old mixed rendering (inline image rows vs `\uFFFC` rows) and misplaces spans.
 */
@RunWith(AndroidJUnit4::class)
class TableImageTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val url = "https://avatars.githubusercontent.com/u/1?v=4"

    private val html = """
        <table>
        <thead><tr><th>Before</th><th>After</th></tr></thead>
        <tbody>
        <tr>
          <td><a href="https://example.com"><img src="$url" alt="before"></a></td>
          <td><img src="$url" alt="after"></td>
        </tr>
        <tr><td>one</td><td>two</td></tr>
        </tbody>
        </table>
    """.trimIndent()

    @Test
    fun tableImagesLoadAndTheRestOfTheTableSurvives() {
        rule.setContent { FaTheme { HtmlText(html = html) } }
        rule.waitForIdle()

        val report = AtomicReference("(no TextView found)")
        rule.waitUntil(timeoutMillis = 25_000) {
            val r = rule.runOnUiThread { inspect() }
            report.set(r)
            r.startsWith("OK")
        }
        val finalReport = report.get()
        assertTrue("table did not render as expected: $finalReport", finalReport.startsWith("OK"))
    }

    /**
     * Table images must be tappable too: README screenshots often live inside a table. Also pins
     * "links win" — the image wrapped in an `<a>` to elsewhere is not tappable.
     */
    @Test
    fun theImageWithoutAnOuterLinkIsClickable() {
        rule.setContent { FaTheme { HtmlText(html = html) } }
        rule.waitForIdle()

        val clicked = rule.runOnUiThread {
            textViews(rule.activity.window.decorView)
                .mapNotNull { it.text as? Spanned }
                .flatMap { text ->
                    text.getSpans(0, text.length, ImageClickSpan::class.java).map { it.url }
                }
        }
        org.junit.Assert.assertEquals(listOf(url), clicked)
    }

    /** Returns `OK ...` or a diagnostic string (so a failing assertion shows what is wrong). */
    private fun inspect(): String {
        val tv: TextView = textViews(rule.activity.window.decorView).firstOrNull()
            ?: return "no TextView in the tree"
        val text = tv.text as? Spanned ?: return "TextView.text is not Spanned: ${tv.text}"

        val spans = text.getSpans(0, text.length, ImageSpan::class.java)
        // Only the decoded image counts: the loading placeholder has bounds from creation, so "has
        // a width" no longer proves the image loaded.
        val widths = spans.map { span ->
            (span.drawable as? UrlDrawable)?.image?.bounds?.width() ?: 0
        }
        val loaded = widths.count { it > 0 }
        val flat = text.toString().replace('\uFFFC', '#').replace('\n', '⏎')

        val bad = mutableListOf<String>()
        if (spans.size < 2) bad += "found only ${spans.size} ImageSpan(s) (expected two images)"
        if (loaded < 2) bad += "only $loaded image(s) have a width (expected 2)"
        // The text-only row must survive; a misplacement loses it.
        if (!flat.contains("one")) bad += "the text row lost 'one'"
        if (!flat.contains("two")) bad += "the text row lost 'two'"
        // Span offsets must stay inside the text (a misplacement gives getSpanStart = -1).
        if (spans.any { text.getSpanStart(it) < 0 }) bad += "an ImageSpan starts outside the text"
        if (text.toString().count { it == '\uFFFC' } < 2) bad += "wrong number of placeholder characters"

        val shape = "spans=${spans.size} loaded=$loaded widths=$widths text=[$flat]"
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
