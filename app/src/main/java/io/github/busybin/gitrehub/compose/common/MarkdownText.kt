package io.github.busybin.gitrehub.compose.common

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import io.github.busybin.gitrehub.compose.theme.LocalCodePalette
import io.github.busybin.gitrehub.provider.markdown.MarkDownProvider
import io.github.busybin.gitrehub.provider.markdown.ReferenceLinks
import io.github.busybin.gitrehub.provider.timeline.HtmlHelper
import io.github.busybin.gitrehub.provider.timeline.MarkdownCodeColors
import io.github.busybin.gitrehub.provider.timeline.MarkdownPalette

/**
 * Markdown / HTML renderer for Compose.
 *
 * Text is rendered by the existing legacy pipeline ([MarkDownProvider] / [HtmlHelper], commonmark
 * + HtmlSpanner + emoji + mention extensions), so the UI keeps the same rendering, link
 * handling and in-app navigation as the GitHub website.
 *
 * All colours come from [MaterialTheme] (see [markdownPalette]); the pipeline otherwise used the
 * legacy theme settings, which made READMEs look wrong whatever the modern theme was.
 */
@Composable
fun MarkdownText(
    markdown: String?,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 14f,
    baseUrl: String? = null,
    linkBaseUrl: String? = null,
    /** `owner/name`, so `#123` references link to that repository's issues. */
    repo: String? = null,
    /** An in-page anchor (`#xxx` in a README) was tapped: anchor name, its vertical pixel offset in the rendered TextView, and the TextView's Y in the window. */
    onAnchor: ((anchor: String, yInView: Int, viewTopInWindow: Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val palette = markdownPalette()
    var preview by remember { mutableStateOf<String?>(null) }
    val anchorBridge = remember { AnchorBridge() }
    anchorBridge.handler = onAnchor

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fallbackPx = with(density) { maxWidth.roundToPx() }
        // Prefer the TextView's real measured px width: maxWidth is Dp and roundToPx uses the
        // composition density, and when they differ (tests without an Activity, rescaled density)
        // tables size columns and images wrong. Use the fallback until measured, then re-render.
        var measuredPx by remember { mutableIntStateOf(0) }
        val widthPx = if (measuredPx > 0) measuredPx else fallbackPx
        val textView = remember {
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                setLineSpacing(0f, 1.15f)
                typeface = Typeface.DEFAULT
                setTextIsSelectable(true)
            }
        }
        val rendered = remember { RenderedMark() }
        anchorBridge.view = textView
        AndroidView(
            factory = { textView },
            update = { view ->
                if (!markdown.isNullOrBlank() && widthPx > 0 && rendered.needs(markdown, widthPx)) {
                    rendered.record(markdown, widthPx)
                    MarkDownProvider.setMdText(
                        view,
                        ReferenceLinks.expand(markdown, repo),
                        widthPx,
                        palette,
                        baseUrl,
                        linkBaseUrl,
                        onImageClick = { preview = it },
                        onAnchorClick = { anchor -> anchorBridge.onAnchor(anchor) },
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { anchorBridge.topInWindow = it.positionInWindow().y }
                .onSizeChanged { if (it.width != measuredPx) measuredPx = it.width },
        )
    }
    preview?.let { url ->
        ImageViewerDialog(url = url, onDismiss = { preview = null })
    }
}

/** Renders raw HTML (e.g. GitHub's server-side README rendering) through the same pipeline. */
@Composable
fun HtmlText(
    html: String?,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 14f,
    baseUrl: String? = null,
    linkBaseUrl: String? = null,
    /** Same `onAnchor` as [MarkdownText]: in-page `#xxx` jumps inside a README. */
    onAnchor: ((anchor: String, yInView: Int, viewTopInWindow: Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val palette = markdownPalette()
    var preview by remember { mutableStateOf<String?>(null) }
    val anchorBridge = remember { AnchorBridge() }
    anchorBridge.handler = onAnchor

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fallbackPx = with(density) { maxWidth.roundToPx() }
        // Prefer the TextView's real measured px width: maxWidth is Dp and roundToPx uses the
        // composition density, and when they differ (tests without an Activity, rescaled density)
        // tables size columns and images wrong. Use the fallback until measured, then re-render.
        var measuredPx by remember { mutableIntStateOf(0) }
        val widthPx = if (measuredPx > 0) measuredPx else fallbackPx
        val textView = remember {
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                setLineSpacing(0f, 1.15f)
                setTextIsSelectable(true)
            }
        }
        val rendered = remember { RenderedMark() }
        anchorBridge.view = textView
        AndroidView(
            factory = { textView },
            update = { view ->
                if (!html.isNullOrBlank() && widthPx > 0 && rendered.needs(html, widthPx)) {
                    rendered.record(html, widthPx)
                    runCatching {
                        HtmlHelper.htmlIntoTextView(
                            view,
                            html,
                            widthPx,
                            palette,
                            baseUrl,
                            linkBaseUrl,
                            onImageClick = { preview = it },
                            onAnchorClick = { anchor -> anchorBridge.onAnchor(anchor) },
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { anchorBridge.topInWindow = it.positionInWindow().y }
                .onSizeChanged { if (it.width != measuredPx) measuredPx = it.width },
        )
    }
    preview?.let { url ->
        ImageViewerDialog(url = url, onDismiss = { preview = null })
    }
}

/**
 * Base URL for **relative images** in a repository's README / issue / release markdown.
 *
 * `raw.githubusercontent.com` serves the file bytes directly (no HTML page in between), which is
 * what an image loader needs. `HEAD` works when the branch is not known; pass the real default
 * branch when there is one.
 */
fun repoRawBaseUrl(owner: String, repo: String, branch: String? = null): String =
    "https://raw.githubusercontent.com/$owner/$repo/${branch?.takeIf { it.isNotBlank() } ?: "HEAD"}/"

/**
 * Base URL for **relative links** in a repository's README (`[docs](docs/x.md)`, `LICENSE`…).
 *
 * GitHub's API leaves those hrefs relative (its own renderer rewrites them to
 * `github.com/{o}/{r}/blob/<ref>/…`), and once they are rewritten that way `SchemeParser`
 * turns them into *our* screens — so a README link opens the file in the app instead of
 * going out to a browser.
 */
fun repoBlobBaseUrl(owner: String, repo: String, branch: String? = null): String =
    "https://github.com/$owner/$repo/blob/${branch?.takeIf { it.isNotBlank() } ?: "HEAD"}/"

/**
 * Reading palette, all taken from the current theme: inline `code` uses the faintest layer so it
 * does not compete with the body (and is never red); code blocks use the theme's own code colours
 * ([LocalCodePalette]); links use `primary`; quotes and dividers use `outlineVariant`.
 */
@Composable
private fun markdownPalette(): MarkdownPalette {
    val scheme = MaterialTheme.colorScheme
    val code = LocalCodePalette.current
    return remember(scheme, code) {
        MarkdownPalette(            text = scheme.onSurface.toArgb(),
            link = scheme.primary.toArgb(),
            inlineCodeBackground = scheme.surfaceContainerHigh.toArgb(),
            inlineCodeText = scheme.onSurfaceVariant.toArgb(),
            codeBackground = code.backgroundArgb,
            codeText = code.plainArgb,
            quote = scheme.outlineVariant.toArgb(),
            divider = scheme.outlineVariant.toArgb(),
            codeColors = MarkdownCodeColors(
                keyword = code.keywordArgb,
                comment = code.commentArgb,
                string = code.stringArgb,
                number = code.numberArgb,
                function = code.functionArgb,
                type = code.typeArgb,
            ),
        )
    }
}

/** Single-line "strip the markup" rendering, for list rows. */
fun stripMarkdown(markdown: String?): String =
    if (markdown.isNullOrBlank()) "" else MarkDownProvider.stripMdText(markdown)

/** Remember which content and width were last rendered, to avoid re-injecting the HTML on every recomposition (which reloads images). */
private class RenderedMark {
    private var html: String? = null
    private var width: Int = 0

    fun needs(content: String, widthPx: Int): Boolean = html != content || width != widthPx

    fun record(content: String, widthPx: Int) {
        html = content
        width = widthPx
    }
}

/**
 * Bridge for in-page anchors. The legacy pipeline does not keep heading ids, so `#foo-bar` is turned
 * back into `foo bar` and searched in the rendered text, then the layout gives that line's top offset.
 * Missing anchors are ignored. Fields are not Compose state: read on click only.
 */
private class AnchorBridge {
    var view: android.widget.TextView? = null
    var topInWindow: Float = 0f
    var handler: ((String, Int, Float) -> Unit)? = null

    fun onAnchor(anchor: String) {
        val textView = view ?: return
        val y = anchorY(textView, anchor) ?: return
        handler?.invoke(anchor, y, topInWindow)
    }
}

private fun anchorY(textView: android.widget.TextView, anchor: String): Int? {
    val layout = textView.layout ?: return null
    val text = textView.text?.toString() ?: return null
    val heading = anchor.removePrefix("user-content-").replace('-', ' ').replace('_', ' ').trim()
    val candidates = listOf(heading, anchor).filter { it.isNotBlank() }
    val index = candidates.map { text.indexOf(it, ignoreCase = true) }
        .filter { it >= 0 }
        .minOrNull() ?: return null
    val line = layout.getLineForOffset(index)
    return layout.getLineTop(line)
}

