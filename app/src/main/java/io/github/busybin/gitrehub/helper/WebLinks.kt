package io.github.busybin.gitrehub.helper

/**
 * Relative URLs inside GitHub's rendered content.
 *
 * The API's HTML flavour (`Accept: application/vnd.github.html+json`, used for READMEs) leaves
 * `<img src>` **relative** — e.g. `screenshots/home.png` or `.github/assets/logo.png`. github.com's
 * own renderer rewrites those to `/raw/<branch>/…`; the API does not. Handing the bare relative
 * string to an image loader fails silently (there is no host to fetch from), which is exactly why
 * README screenshots never showed up while avatars — which are always absolute — did.
 *
 * Pure string work on purpose: it is covered by JVM tests (see `WebLinksTest`).
 */
object WebLinks {

    /**
     * Whether [url] can already be fetched as-is: it carries a scheme (`https:`, `data:`,
     * `mailto:`…), is protocol-relative (`//host/…`) or is an in-page fragment (`#section`).
     */
    fun isAbsolute(url: String): Boolean {
        if (url.startsWith("//") || url.startsWith("#")) return true
        val colon = url.indexOf(':')
        if (colon <= 0) return false
        return url.take(colon).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
    }

    /**
     * Resolves [url] against [baseUrl]. Absolute URLs are returned untouched; a leading `/` means
     * the site root (github.com); anything else is joined onto the base (which may or may not end
     * in `/`).
     *
     * @return the resolved URL, or [url] unchanged when there is no base to resolve against.
     */
    fun resolve(baseUrl: String?, url: String?): String? {
        if (url.isNullOrBlank() || isAbsolute(url)) return url
        if (url.startsWith("/")) return "https://github.com$url"
        val base = baseUrl?.takeIf { it.isNotBlank() } ?: return url
        return base.trimEnd('/') + "/" + url.removePrefix("./")
    }
}
