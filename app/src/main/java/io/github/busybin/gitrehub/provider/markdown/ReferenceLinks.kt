package io.github.busybin.gitrehub.provider.markdown

/**
 * Turns GitHub cross-references in Markdown (`#123`, `owner/repo#123`) into links, the way the
 * website renders them. Code spans and fenced blocks are left alone; mentions already have their
 * own extension and are not touched here.
 */
object ReferenceLinks {

    private val CROSS = Regex("""(?<![\w/.\-])([A-Za-z0-9][\w.\-]*/[A-Za-z0-9][\w.\-]*)#(\d+)\b""")
    private val LOCAL = Regex("""(?<![\[\w/&(])#(\d+)\b""")

    /** [repo] is `owner/name`; without it local `#123` references stay plain text. */
    fun expand(markdown: String, repo: String?): String {
        if (markdown.isEmpty() || (repo.isNullOrBlank() && !markdown.contains('/'))) return markdown
        val out = StringBuilder(markdown.length + 64)
        var inFence = false
        markdown.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence
                out.append(line)
            } else if (inFence) {
                out.append(line)
            } else {
                out.append(linkLine(line, repo))
            }
            out.append('\n')
        }
        return out.toString()
    }

    /** Odd indices between backticks are code spans and must not be rewritten. */
    private fun linkLine(line: String, repo: String?): String {
        val parts = line.split('`')
        val out = StringBuilder(line.length + 16)
        parts.forEachIndexed { index, part ->
            if (index > 0) out.append('`')
            out.append(if (index % 2 == 0) replaceRefs(part, repo) else part)
        }
        return out.toString()
    }

    private fun replaceRefs(text: String, repo: String?): String {
        var result = CROSS.replace(text) { match ->
            val slug = match.groupValues[1]
            val number = match.groupValues[2]
            "[$slug#$number](https://github.com/$slug/issues/$number)"
        }
        if (!repo.isNullOrBlank()) {
            result = LOCAL.replace(result) { match ->
                val number = match.groupValues[1]
                "[#$number](https://github.com/$repo/issues/$number)"
            }
        }
        return result
    }
}
