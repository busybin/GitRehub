package io.github.busybin.gitrehub.core.net

/**
 * The `Link` header GitHub sends for paginated endpoints
 * (`<...page=2>; rel="next", <...page=40>; rel="last"`).
 *
 * Page numbers are what the app actually needs: `per_page=1` + `rel="last"` is how the exact
 * total is read for lists whose JSON only carries a combined count.
 */
data class PageLinks(val next: Int? = null, val last: Int? = null) {
    companion object {
        fun parse(header: String?): PageLinks {
            if (header.isNullOrBlank()) return PageLinks()
            var next: Int? = null
            var last: Int? = null
            header.split(',').forEach { part ->
                val target = part.substringAfter('<', "").substringBefore('>', "")
                val rel = part.substringAfter("rel=\"", "").substringBefore('"', "")
                if (target.isBlank() || rel.isBlank()) return@forEach
                val page = Regex("[?&]page=(\\d+)").find(target)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@forEach
                when (rel) {
                    "next" -> next = page
                    "last" -> last = page
                }
            }
            return PageLinks(next, last)
        }
    }
}
