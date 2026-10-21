package io.github.busybin.gitrehub.provider.syntax

/**
 * A small tokenizer for theme-colored syntax highlighting. Lexical only (comments / strings /
 * numbers / keywords / functions / types / annotations), no real parsing: that is the level of
 * distinction reading code needs. Pure logic, no Android, so it runs under JVM tests (see
 * `SyntaxTest`).
 */
enum class TokenKind { COMMENT, STRING, NUMBER, KEYWORD, FUNCTION, TYPE, ANNOTATION }

data class Token(val start: Int, val end: Int, val kind: TokenKind)

private class LanguageSpec(
    val lineComments: List<String>,
    val blockComments: List<Pair<String, String>> = emptyList(),
    val stringDelims: List<Char> = listOf('"', '\''),
    keywords: Set<String> = emptySet(),
    val caseInsensitive: Boolean = false,
    val annotations: Boolean = false,
) {
    /** Keywords are lowercased up front for case-insensitive languages (SQL, Dockerfile). */
    val keywords: Set<String> =
        if (caseInsensitive) keywords.mapTo(HashSet()) { it.lowercase() } else keywords
}

private val C_LIKE_KEYWORDS = setOf(
    "abstract", "as", "assert", "async", "await", "break", "case", "catch", "class", "const",
    "constructor", "continue", "data", "default", "delegate", "do", "dynamic", "else", "enum",
    "export", "extends", "external", "false", "final", "finally", "for", "fun", "function", "get",
    "if", "implements", "import", "in", "inline", "instanceof", "interface", "internal", "is",
    "lateinit", "let", "new", "null", "object", "operator", "override", "package", "private",
    "protected", "public", "return", "sealed", "set", "static", "super", "suspend", "switch",
    "this", "throw", "throws", "true", "try", "typealias", "typeof", "val", "var", "void",
    "when", "while", "yield",
)

private val PYTHON_KEYWORDS = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif",
    "else", "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is",
    "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while",
    "with", "yield", "self",
)

private val SHELL_KEYWORDS = setOf(
    "case", "do", "done", "elif", "else", "esac", "fi", "for", "function", "if", "in", "local",
    "return", "then", "until", "while", "export", "source", "echo", "cd", "exit", "set", "unset",
    "readonly", "declare", "printf",
)

private val GO_KEYWORDS = setOf(
    "break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough", "for",
    "func", "go", "goto", "if", "import", "interface", "map", "package", "range", "return",
    "select", "struct", "switch", "type", "var", "nil", "true", "false",
)

private val RUST_KEYWORDS = setOf(
    "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum", "extern",
    "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod", "move", "mut", "pub",
    "ref", "return", "self", "Self", "static", "struct", "super", "trait", "true", "type", "unsafe",
    "use", "where", "while",
)

private val SQL_KEYWORDS = setOf(
    "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create",
    "table", "drop", "alter", "join", "left", "right", "inner", "outer", "on", "group", "by",
    "order", "having", "limit", "offset", "and", "or", "not", "null", "primary", "key", "foreign",
    "references", "index", "distinct", "as", "union", "all", "case", "when", "then", "else", "end",
)

private val RUBY_KEYWORDS = setOf(
    "alias", "and", "begin", "break", "case", "class", "def", "defined?", "do", "else", "elsif",
    "end", "ensure", "false", "for", "if", "in", "module", "next", "nil", "not", "or", "redo",
    "rescue", "retry", "return", "self", "super", "then", "true", "undef", "unless", "until",
    "when", "while", "yield", "puts", "require", "attr_accessor",
)

private val SPECS: Map<String, LanguageSpec> = buildMap {
    put("kotlin", LanguageSpec(listOf("//"), listOf("/*" to "*/"), keywords = C_LIKE_KEYWORDS, annotations = true))
    put("java", LanguageSpec(listOf("//"), listOf("/*" to "*/"), keywords = C_LIKE_KEYWORDS, annotations = true))
    put("javascript", LanguageSpec(listOf("//"), listOf("/*" to "*/"), listOf('"', '\'', '`'), C_LIKE_KEYWORDS))
    put("typescript", LanguageSpec(listOf("//"), listOf("/*" to "*/"), listOf('"', '\'', '`'), C_LIKE_KEYWORDS))
    put("c", LanguageSpec(listOf("//"), listOf("/*" to "*/"), keywords = C_LIKE_KEYWORDS))
    put("cpp", LanguageSpec(listOf("//"), listOf("/*" to "*/"), keywords = C_LIKE_KEYWORDS))
    put("csharp", LanguageSpec(listOf("//"), listOf("/*" to "*/"), keywords = C_LIKE_KEYWORDS, annotations = true))
    put("go", LanguageSpec(listOf("//"), listOf("/*" to "*/"), listOf('"', '`'), GO_KEYWORDS))
    put("rust", LanguageSpec(listOf("//"), listOf("/*" to "*/"), keywords = RUST_KEYWORDS, annotations = true))
    put("swift", LanguageSpec(listOf("//"), listOf("/*" to "*/"), keywords = C_LIKE_KEYWORDS, annotations = true))
    put("php", LanguageSpec(listOf("//", "#"), listOf("/*" to "*/"), keywords = C_LIKE_KEYWORDS))
    put("scala", LanguageSpec(listOf("//"), listOf("/*" to "*/"), keywords = C_LIKE_KEYWORDS, annotations = true))
    put("dart", LanguageSpec(listOf("//"), listOf("/*" to "*/"), keywords = C_LIKE_KEYWORDS, annotations = true))
    put("python", LanguageSpec(listOf("#"), stringDelims = listOf('"', '\''), keywords = PYTHON_KEYWORDS))
    put("ruby", LanguageSpec(listOf("#"), stringDelims = listOf('"', '\''), keywords = RUBY_KEYWORDS))
    put("shell", LanguageSpec(listOf("#"), keywords = SHELL_KEYWORDS))
    put("lua", LanguageSpec(listOf("--"), listOf("--[[" to "]]"), keywords = RUBY_KEYWORDS))
    put("sql", LanguageSpec(listOf("--"), listOf("/*" to "*/"), keywords = SQL_KEYWORDS, caseInsensitive = true))
    put("json", LanguageSpec(emptyList(), keywords = setOf("true", "false", "null")))
    put("yaml", LanguageSpec(listOf("#"), keywords = setOf("true", "false", "null", "yes", "no")))
    put("toml", LanguageSpec(listOf("#"), keywords = setOf("true", "false")))
    put("ini", LanguageSpec(listOf("#", ";"), keywords = setOf("true", "false")))
    put("properties", LanguageSpec(listOf("#", "!"), keywords = emptySet()))
    put("dockerfile", LanguageSpec(listOf("#"), keywords = setOf("FROM", "RUN", "CMD", "COPY", "ADD", "ENTRYPOINT", "ENV", "ARG", "WORKDIR", "EXPOSE", "VOLUME", "USER", "LABEL", "HEALTHCHECK", "SHELL", "ONBUILD", "STOPSIGNAL"), caseInsensitive = true))
    put("makefile", LanguageSpec(listOf("#"), keywords = setOf("ifeq", "ifneq", "ifdef", "ifndef", "else", "endif", "include", "define", "endef", "export")))
    put("css", LanguageSpec(emptyList(), listOf("/*" to "*/"), keywords = setOf("@media", "@import", "@keyframes")))
    put("markup", LanguageSpec(emptyList(), listOf("<!--" to "-->")))
    put("markdown", LanguageSpec(emptyList(), listOf("<!--" to "-->")))
}

private val BY_EXTENSION: Map<String, String> = mapOf(
    "kt" to "kotlin", "kts" to "kotlin", "java" to "java",
    "js" to "javascript", "mjs" to "javascript", "cjs" to "javascript", "jsx" to "javascript",
    "ts" to "typescript", "tsx" to "typescript", "vue" to "javascript",
    "c" to "c", "h" to "c", "cc" to "cpp", "cpp" to "cpp", "cxx" to "cpp", "hpp" to "cpp", "hh" to "cpp",
    "cs" to "csharp", "go" to "go", "rs" to "rust", "swift" to "swift", "php" to "php",
    "scala" to "scala", "dart" to "dart",
    "py" to "python", "pyw" to "python",
    "rb" to "ruby", "gemspec" to "ruby", "rake" to "ruby",
    "sh" to "shell", "bash" to "shell", "zsh" to "shell", "fish" to "shell", "ksh" to "shell",
    "lua" to "lua", "sql" to "sql",
    "json" to "json", "json5" to "json", "geojson" to "json",
    "yml" to "yaml", "yaml" to "yaml",
    "toml" to "toml", "ini" to "ini", "cfg" to "ini", "conf" to "ini", "properties" to "properties",
    "css" to "css", "scss" to "css", "less" to "css",
    "html" to "markup", "htm" to "markup", "xml" to "markup", "svg" to "markup", "gradle" to "kotlin",
    "pro" to "makefile", "mk" to "makefile", "mak" to "makefile",
    "md" to "markdown", "markdown" to "markdown", "rst" to "markdown", "txt" to "markdown",
)

object Syntax {

    /** Platform-specific aliases → our canonical language names. */
    private val ALIASES: Map<String, String> = mapOf(
        "js" to "javascript", "jsx" to "javascript", "node" to "javascript", "mjs" to "javascript",
        "ts" to "typescript", "tsx" to "typescript",
        "py" to "python", "python3" to "python",
        "rb" to "ruby",
        "sh" to "shell", "bash" to "shell", "zsh" to "shell", "console" to "shell",
        "shell-session" to "shell", "shellsession" to "shell", "terminal" to "shell",
        "kt" to "kotlin", "kts" to "kotlin",
        "cs" to "csharp", "c#" to "csharp",
        "rs" to "rust",
        "golang" to "go",
        "yml" to "yaml",
        "c++" to "cpp", "cc" to "cpp", "cxx" to "cpp",
        "htm" to "markup", "html" to "markup", "xml" to "markup",
        "md" to "markdown",
        "text" to "markdown", "plaintext" to "markdown",
    )

    /** Normalize a raw language name (`language-x`, `highlight-source-x`, or a file extension). */
    fun normalizeLanguage(raw: String?): String? {
        val name = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        ALIASES[name]?.let { return it }
        if (name in SPECS) return name
        return BY_EXTENSION[name]
    }

    /** Guess the language from a file name/path; null means plain text, no highlighting. */
    fun languageOf(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val name = path.substringAfterLast('/').lowercase()
        when {
            name == "dockerfile" || name.startsWith("dockerfile.") -> return "dockerfile"
            name == "makefile" || name == "gnumakefile" -> return "makefile"
            name.startsWith(".env") -> return "ini"
        }
        val ext = name.substringAfterLast('.', "")
        if (ext.isEmpty()) return null
        return BY_EXTENSION[ext]
    }

    /** Tokenize [code]; returns an empty list for languages we cannot highlight. */
    fun tokenize(code: String, language: String?): List<Token> {
        val spec = language?.let { SPECS[it] } ?: return emptyList()
        val tokens = ArrayList<Token>(64)
        var i = 0
        while (i < code.length) {
            val c = code[i]

            val line = spec.lineComments.firstOrNull { code.startsWith(it, i) }
            if (line != null) {
                val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                tokens += Token(i, end, TokenKind.COMMENT)
                i = end
                continue
            }

            val block = spec.blockComments.firstOrNull { code.startsWith(it.first, i) }
            if (block != null) {
                val close = code.indexOf(block.second, i + block.first.length)
                val end = if (close < 0) code.length else close + block.second.length
                tokens += Token(i, end, TokenKind.COMMENT)
                i = end
                continue
            }

            if (c in spec.stringDelims) {
                val end = stringEnd(code, i, c)
                tokens += Token(i, end, TokenKind.STRING)
                i = end
                continue
            }

            if (c.isDigit() && (i == 0 || !isIdentPart(code[i - 1]))) {
                val end = numberEnd(code, i)
                tokens += Token(i, end, TokenKind.NUMBER)
                i = end
                continue
            }

            if (c == '@' && spec.annotations) {
                val end = identEnd(code, i + 1)
                if (end > i + 1) {
                    tokens += Token(i, end, TokenKind.ANNOTATION)
                    i = end
                    continue
                }
            }

            if (identStart(c)) {
                val end = identEnd(code, i)
                val word = code.substring(i, end)
                val key = if (spec.caseInsensitive) word.lowercase() else word
                val kind = when {
                    key in spec.keywords -> TokenKind.KEYWORD
                    end < code.length && code[end] == '(' -> TokenKind.FUNCTION
                    isTypeName(word) -> TokenKind.TYPE
                    else -> null
                }
                if (kind != null) tokens += Token(i, end, kind)
                i = end
                continue
            }

            i++
        }
        return tokens
    }

    private fun stringEnd(code: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < code.length) {
            val c = code[i]
            if (c == '\\') {
                i += 2
                continue
            }
            // Unterminated string: stop at the newline instead of swallowing the whole file.
            if (c == '\n' && quote != '`') return i
            if (c == quote) return i + 1
            i++
        }
        return code.length
    }

    private fun numberEnd(code: String, start: Int): Int {
        var i = start
        while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '.' || code[i] == '_')) i++
        return i
    }

    private fun identStart(c: Char) = c.isLetter() || c == '_' || c == '$'

    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    private fun identEnd(code: String, start: Int): Int {
        var i = start
        while (i < code.length && isIdentPart(code[i])) i++
        return i
    }

    /** Leading uppercase (including ALL_CAPS constants) counts as a type name — imprecise but enough. */
    private fun isTypeName(word: String) =
        word.length > 1 && word.first().isUpperCase() && word.any { it.isLowerCase() } || word.all { it.isUpperCase() && it.isLetter() }
}
