package io.github.busybin.gitrehub.compose.common

import android.content.Context
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.busybin.gitrehub.provider.markdown.MarkDownProvider

/**
 * "Print" / "Save as PDF" for a markdown body: commonmark turns it into HTML, a headless WebView
 * renders it, and the system print service handles preview, paper size and PDF export.
 *
 * The WebView is never attached to a window (the print adapter renders its own copy); it is kept
 * alive until the job is handed over, because printing runs after this function returns.
 */
object PrintSupport {

    private var keepAlive: WebView? = null

    fun printMarkdown(context: Context, title: String, markdown: String) {
        val body = runCatching { MarkDownProvider.markdownToHtml(markdown) }
            .getOrElse { "<pre>" + markdown.replace("<", "&lt;") + "</pre>" }
        val html = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              body { font-family: -apple-system, Roboto, sans-serif; font-size: 12pt; line-height: 1.5; color: #111; }
              pre, code { font-family: monospace; font-size: 9pt; background: #f5f5f5; }
              pre { padding: 8px; white-space: pre-wrap; word-break: break-word; }
              img { max-width: 100%; height: auto; }
              table { border-collapse: collapse; } td, th { border: 1px solid #ccc; padding: 4px; }
              h1, h2, h3 { page-break-after: avoid; }
            </style></head><body>$body</body></html>
        """.trimIndent()

        val web = WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    if (manager != null) {
                        val adapter = view.createPrintDocumentAdapter(title.ifBlank { "GitRehub" })
                        runCatching { manager.print(title.ifBlank { "GitRehub" }, adapter, null) }
                    }
                }
            }
            loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
        keepAlive?.destroy()
        keepAlive = web
    }
}
