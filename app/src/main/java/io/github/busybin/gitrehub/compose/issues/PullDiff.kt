package io.github.busybin.gitrehub.compose.issues

/**
 * One line of a unified diff.
 *
 * [text] has the leading marker stripped; [oldLine]/[newLine] are the file line numbers the line
 * belongs to (null on the side it does not exist). Inline comments need exactly that pair: GitHub
 * anchors a review comment on a **file line** plus the side it belongs to.
 */
data class DiffLine(
    val text: String,
    val kind: Kind,
    val oldLine: Int? = null,
    val newLine: Int? = null,
) {
    enum class Kind { CONTEXT, ADDED, REMOVED, HUNK, NOTE }

    /** GitHub's `side` for a comment on this line; null when the line cannot be commented on. */
    val commentSide: String?
        get() = when (kind) {
            Kind.ADDED, Kind.CONTEXT -> "RIGHT"
            Kind.REMOVED -> "LEFT"
            else -> null
        }

    /** The file line a comment on this line refers to. */
    val commentLine: Int?
        get() = when (kind) {
            Kind.ADDED, Kind.CONTEXT -> newLine
            Kind.REMOVED -> oldLine
            else -> null
        }
}

private val HUNK_HEADER = Regex("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$")

/**
 * Parses the `patch` GitHub returns for a changed file (the same text a `git diff` shows).
 *
 * Line numbers start at the hunk header and advance per line kind, so every added/context/removed
 * line knows both file line numbers — that is what the review API wants.
 */
fun parsePatch(patch: String?): List<DiffLine> {
    if (patch.isNullOrBlank()) return emptyList()
    val out = ArrayList<DiffLine>()
    var oldLine = 0
    var newLine = 0
    var inHunk = false
    patch.lineSequence().forEach { line ->
        if (line.isEmpty()) {
            out += DiffLine("", DiffLine.Kind.NOTE)
            return@forEach
        }
        val header = HUNK_HEADER.matchEntire(line)
        if (header != null) {
            oldLine = header.groupValues[1].toIntOrNull() ?: 0
            newLine = header.groupValues[2].toIntOrNull() ?: 0
            inHunk = true
            out += DiffLine(line, DiffLine.Kind.HUNK)
            return@forEach
        }
        if (!inHunk || line.startsWith("\\")) {
            out += DiffLine(line, DiffLine.Kind.NOTE)
            return@forEach
        }
        when (line[0]) {
            '+' -> {
                out += DiffLine(line.substring(1), DiffLine.Kind.ADDED, newLine = newLine)
                newLine++
            }

            '-' -> {
                out += DiffLine(line.substring(1), DiffLine.Kind.REMOVED, oldLine = oldLine)
                oldLine++
            }

            else -> {
                out += DiffLine(line.substring(1), DiffLine.Kind.CONTEXT, oldLine, newLine)
                oldLine++
                newLine++
            }
        }
    }
    return out
}
