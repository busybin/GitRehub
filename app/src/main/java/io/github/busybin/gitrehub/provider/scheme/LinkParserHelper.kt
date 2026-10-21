package io.github.busybin.gitrehub.provider.scheme

import android.net.Uri
import io.github.busybin.gitrehub.helper.PrefGetter
import java.util.Locale

/** Helpers for GitHub Enterprise URLs and for cleaning escaped characters out of links. */
object LinkParserHelper {
    @JvmStatic
    fun isEnterprise(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val base = PrefGetter.enterpriseUrl?.trim()?.trimEnd('/')?.lowercase(Locale.ROOT)
        if (base.isNullOrBlank()) return false
        val target = url.lowercase(Locale.ROOT)
        val host = runCatching { Uri.parse(base).host }.getOrNull() ?: return false
        return target.contains(host) || target.contains(base)
    }

    private val HTML_ESCAPES = mapOf(
        "&quot;" to "\"",
        "&apos;" to "'",
        "&lt;" to "<",
        "&gt;" to ">",
        "&amp;" to "&",
    )

    /** GitHub sometimes puts HTML-escaped query parameters into deep links. */
    @JvmStatic
    fun parseReferenceSymbols(uri: Uri): Uri {
        var text = uri.toString()
        HTML_ESCAPES.forEach { (escaped, plain) -> text = text.replace(escaped, plain) }
        return Uri.parse(text)
    }
}
