package io.github.busybin.gitrehub.compose

import android.text.Spanned
import android.text.style.URLSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.busybin.gitrehub.compose.common.HtmlText
import io.github.busybin.gitrehub.compose.theme.FaTheme
import io.github.busybin.gitrehub.provider.timeline.handler.ImageClickSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Links and images in README bodies. GitHub's API leaves `href`/`src` relative (Deluge's README uses
 * `docs/ARCHITECTURE.md`, `LICENSE`, `screenshots/home.png`), so these tests pin which URL ends up on
 * the span after rendering — the only thing `SchemeParser` uses to decide in-app navigation.
 */
@RunWith(AndroidJUnit4::class)
class MarkdownLinkTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val raw = "https://raw.githubusercontent.com/busybin/DelugeLauncher/main/"
    private val blob = "https://github.com/busybin/DelugeLauncher/blob/main/"

    @Test
    fun relativeHrefIsResolvedIntoAnAbsoluteGithubUrl() {
        rule.setContent {
            FaTheme {
                HtmlText(
                    html = "<p><a href=\"docs/ARCHITECTURE.md\">architecture</a></p>",
                    linkBaseUrl = blob,
                )
            }
        }
        rule.waitForIdle()

        val links = rule.runOnUiThread { linkUrls() }
        assertTrue(
            "relative href was not resolved to a github.com URL (SchemeParser cannot route it, so the tap does nothing): $links",
            links.contains(blob + "docs/ARCHITECTURE.md"),
        )
    }

    @Test
    fun anImageIsClickableAndCarriesTheResolvedUrl() {
        // A GitHub README screenshot looks like this: the image wrapped in an <a> to the same file.
        rule.setContent {
            FaTheme {
                HtmlText(
                    html = "<p><a href=\"screenshots/home.png\"><img src=\"screenshots/home.png\"></a></p>",
                    baseUrl = raw,
                    linkBaseUrl = blob,
                )
            }
        }
        rule.waitForIdle()

        val clicked = rule.runOnUiThread { imageClickUrls() }
        assertEquals(
            "image has no tap-to-view-larger span, or the URL was not resolved to an absolute one",
            listOf(raw + "screenshots/home.png"),
            clicked,
        )
    }

    @Test
    fun anImageThatLinksElsewhereStaysALink() {
        // A badge: tapping the image should open the linked page, not the viewer.
        rule.setContent {
            FaTheme {
                HtmlText(
                    html = "<p><a href=\"https://example.com/build\"><img src=\"badge.svg\"></a></p>",
                    baseUrl = raw,
                    linkBaseUrl = blob,
                )
            }
        }
        rule.waitForIdle()

        val clicks = rule.runOnUiThread { imageClickUrls() }
        val links = rule.runOnUiThread { linkUrls() }
        assertNull("an image linking elsewhere must not be treated as view-larger: $clicks", clicks.firstOrNull())
        assertTrue("the badge link was lost: $links", links.contains("https://example.com/build"))
    }

    private fun imageClickUrls(): List<String> = textViews()
        .mapNotNull { it.text as? Spanned }
        .flatMap { text -> text.getSpans(0, text.length, ImageClickSpan::class.java).map { it.url } }

    private fun linkUrls(): List<String> = textViews()
        .mapNotNull { it.text as? Spanned }
        .flatMap { text ->
            text.getSpans(0, text.length, URLSpan::class.java).mapNotNull { it.url }
        }

    private fun textViews(): List<TextView> {
        val out = mutableListOf<TextView>()
        fun walk(v: View) {
            if (v is TextView) out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(rule.activity.window.decorView)
        return out
    }
}
