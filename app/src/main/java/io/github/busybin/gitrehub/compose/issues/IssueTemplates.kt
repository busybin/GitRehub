package io.github.busybin.gitrehub.compose.issues

import io.github.busybin.gitrehub.compose.data.RepoContent

/**
 * One entry of a repository's issue templates.
 *
 * GitHub supports two flavours: a Markdown file (front matter + body) and an issue **form**
 * (`.yml`), which the official mobile client does not render at all. A form is turned into a
 * Markdown scaffold here — the same sections the web form would ask for — instead of a native
 * form builder: the result is editable in the normal body field and works with any form version.
 */
data class IssueTemplate(
    val name: String,
    val description: String? = null,
    val titlePrefix: String? = null,
    val body: String,
    val isForm: Boolean = false,
)

object IssueTemplates {

    private val ROOT_FILES = listOf(
        ".github/ISSUE_TEMPLATE.md",
        "ISSUE_TEMPLATE.md",
        "docs/ISSUE_TEMPLATE.md",
    )

    suspend fun load(owner: String, repo: String): List<IssueTemplate> {
        val out = mutableListOf<IssueTemplate>()
        val entries = runCatching {
            RepoContent.list(owner, repo, ".github/ISSUE_TEMPLATE", null)
        }.getOrDefault(emptyList())
        entries.filter { !it.isDir }
            .filter { entry ->
                val name = entry.name?.lowercase().orEmpty()
                (name.endsWith(".md") || name.endsWith(".yml") || name.endsWith(".yaml")) &&
                    !name.startsWith("config.")
            }
            .forEach { entry ->
                val text = runCatching { RepoContent.text(entry) }.getOrNull() ?: return@forEach
                val fallback = entry.name.orEmpty().substringBeforeLast('.')
                out += parse(text, fallback)
            }
        if (out.isEmpty()) {
            ROOT_FILES.forEach { path ->
                val entry = runCatching { RepoContent.file(owner, repo, path, null) }.getOrNull()
                    ?: return@forEach
                val text = runCatching { RepoContent.text(entry) }.getOrNull() ?: return@forEach
                out += parse(text, "ISSUE_TEMPLATE")
            }
        }
        return out
    }

    private fun parse(raw: String, fallbackName: String): IssueTemplate {
        val isForm = raw.trimStart().startsWith("name:", ignoreCase = false) ||
            raw.lineSequence().take(4).any { it.trimStart().startsWith("- type:") }
        return if (isForm) parseForm(raw, fallbackName) else parseMarkdown(raw, fallbackName)
    }

    private fun parseMarkdown(raw: String, fallbackName: String): IssueTemplate {
        var body = raw
        val meta = mutableMapOf<String, String>()
        if (raw.startsWith("---")) {
            val end = raw.indexOf("\n---", startIndex = 3)
            if (end > 0) {
                raw.substring(3, end).lineSequence().forEach { line ->
                    val at = line.indexOf(':')
                    if (at > 0) {
                        meta[line.take(at).trim().lowercase()] =
                            line.drop(at + 1).trim().trim('"', '\'')
                    }
                }
                body = raw.substringAfter("\n---", "").removePrefix("\n")
            }
        }
        val name = meta["name"]?.takeIf { it.isNotBlank() } ?: fallbackName
        return IssueTemplate(
            name = name,
            description = meta["about"] ?: meta["description"],
            titlePrefix = meta["title"]?.takeIf { it.isNotBlank() },
            body = body.trim(),
        )
    }

    private fun parseForm(raw: String, fallbackName: String): IssueTemplate {
        fun value(key: String): String? = raw.lineSequence()
            .firstOrNull { it.trimStart().startsWith("$key:") }
            ?.substringAfter(':')?.trim()?.trim('"', '\'')

        val sections = mutableListOf<String>()
        val lines = raw.lines()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart()
            if (!trimmed.startsWith("- type:")) {
                index++
                continue
            }
            val type = trimmed.substringAfter(':').trim()
            var label: String? = null
            var description: String? = null
            var block: String? = null
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith("- type:")) {
                val current = lines[index]
                val currentTrimmed = current.trimStart()
                when {
                    currentTrimmed.startsWith("label:") -> label = afterColon(currentTrimmed)
                    currentTrimmed.startsWith("description:") -> description = afterColon(currentTrimmed)
                    currentTrimmed.startsWith("value:") -> {
                        val inline = afterColon(currentTrimmed)
                        block = if (inline == "|" || inline == ">" || inline.isBlank()) {
                            buildString {
                                index++
                                while (index < lines.size && isIndented(lines[index])) {
                                    appendLine(lines[index].trimStart())
                                    index++
                                }
                            }.trimEnd()
                        } else {
                            inline
                        }
                        continue
                    }
                }
                index++
            }
            when {
                type == "markdown" && !block.isNullOrBlank() -> sections += block.trim()
                type == "checkboxes" && !label.isNullOrBlank() -> sections += "- [ ] $label"
                !label.isNullOrBlank() -> sections += buildString {
                    append("### ").append(label)
                    appendLine()
                    appendLine()
                    if (!description.isNullOrBlank()) appendLine("<!-- $description -->")
                }.trimEnd()
            }
        }
        val name = value("name") ?: fallbackName
        return IssueTemplate(
            name = name,
            description = value("description") ?: value("about"),
            titlePrefix = value("title"),
            body = sections.joinToString("\n\n"),
            isForm = true,
        )
    }

    private fun isIndented(line: String): Boolean =
        line.isBlank() || line.startsWith(" ") || line.startsWith("\t")

    private fun afterColon(line: String): String =
        line.substringAfter(':').trim().trim('"', '\'')
}
