package io.github.busybin.gitrehub.provider.syntax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lexer behind code colouring. Pure text in, ranges out — which is exactly why it lives on the
 * JVM test set instead of on an emulator.
 */
class SyntaxTest {

    private fun kinds(code: String, language: String?): List<Pair<TokenKind, String>> =
        Syntax.tokenize(code, language).map { it.kind to code.substring(it.start, it.end) }

    private fun texts(code: String, language: String?, kind: TokenKind): List<String> =
        kinds(code, language).filter { it.first == kind }.map { it.second }

    @Test
    fun languageIsGuessedFromTheFileName() {
        assertEquals("kotlin", Syntax.languageOf("app/src/main/java/Foo.kt"))
        assertEquals("kotlin", Syntax.languageOf("build.gradle"))
        assertEquals("python", Syntax.languageOf("scripts/tool.py"))
        assertEquals("markup", Syntax.languageOf("res/layout/main.xml"))
        assertEquals("dockerfile", Syntax.languageOf("deploy/Dockerfile"))
        assertEquals("makefile", Syntax.languageOf("Makefile"))
        assertEquals("ini", Syntax.languageOf(".env"))
        // Unknown names must not pretend to be known — callers render plain text.
        assertEquals(null, Syntax.languageOf("LICENSE"))
        assertEquals(null, Syntax.languageOf("blob.bin"))
        assertEquals(null, Syntax.languageOf(null))
    }

    @Test
    fun kotlinGetsCommentsAnnotationsKeywordsStringsNumbersAndNames() {
        // Non-ASCII comment text: the lexer must keep CJK spans intact.
        val code = """
            // 一行注释
            @Composable
            fun greet(name: String): String {
                val n = 42
                return "hi " + name
            }
        """.trimIndent()

        assertEquals(listOf("// 一行注释"), texts(code, "kotlin", TokenKind.COMMENT))
        assertEquals(listOf("@Composable"), texts(code, "kotlin", TokenKind.ANNOTATION))
        assertEquals(listOf("fun", "val", "return"), texts(code, "kotlin", TokenKind.KEYWORD))
        assertEquals(listOf("\"hi \""), texts(code, "kotlin", TokenKind.STRING))
        assertEquals(listOf("42"), texts(code, "kotlin", TokenKind.NUMBER))
        assertEquals(listOf("greet"), texts(code, "kotlin", TokenKind.FUNCTION))
        assertTrue("String" in texts(code, "kotlin", TokenKind.TYPE))
    }

    @Test
    fun blockCommentsSpanLines() {
        val code = "/* one\n   two */\nval x = 1"
        assertEquals(listOf("/* one\n   two */"), texts(code, "kotlin", TokenKind.COMMENT))
        assertEquals(listOf("val"), texts(code, "kotlin", TokenKind.KEYWORD))
    }

    @Test
    fun pythonAndShellUseHashComments() {
        assertEquals(listOf("# note"), texts("# note\nTrue", "python", TokenKind.COMMENT))
        assertEquals(listOf("True"), texts("# note\nTrue", "python", TokenKind.KEYWORD))
        assertEquals(listOf("# note"), texts("# note\nif true; then", "shell", TokenKind.COMMENT))
        assertEquals(listOf("if", "then"), texts("# note\nif true; then", "shell", TokenKind.KEYWORD))
    }

    @Test
    fun jsonAndYamlHighlightLiteralsAndNumbers() {
        val json = """{"name": "gitrehub", "stars": 12, "public": true}"""
        assertEquals(
            listOf("\"name\"", "\"gitrehub\"", "\"stars\"", "\"public\""),
            texts(json, "json", TokenKind.STRING),
        )
        assertEquals(listOf("12"), texts(json, "json", TokenKind.NUMBER))
        assertEquals(listOf("true"), texts(json, "json", TokenKind.KEYWORD))

        val yaml = "name: gitrehub\npublic: true"
        assertEquals(listOf("true"), texts(yaml, "yaml", TokenKind.KEYWORD))
    }

    @Test
    fun sqlKeywordsAreCaseInsensitive() {
        val code = "SELECT id FROM users"
        assertEquals(listOf("SELECT", "FROM"), texts(code, "sql", TokenKind.KEYWORD))
    }

    /**
     * An unterminated quote must not swallow the whole file as a string — that would grey out
     * everything below, worse than no highlighting.
     */
    @Test
    fun anUnterminatedStringStopsAtTheLineEnd() {
        val code = "val s = \"oops\nval t = 2"
        assertEquals(listOf("\"oops"), texts(code, "kotlin", TokenKind.STRING))
        assertEquals(listOf("val", "val"), texts(code, "kotlin", TokenKind.KEYWORD))
        assertEquals(listOf("2"), texts(code, "kotlin", TokenKind.NUMBER))
    }

    @Test
    fun plainTextIsLeftAlone() {
        assertEquals(emptyList<Pair<TokenKind, String>>(), kinds("just words, no code", "markdown"))
        assertEquals(emptyList<Pair<TokenKind, String>>(), kinds("val x = 1", null))
    }

    /**
     * Language names arrive from several sources (`class="language-x"`, GitHub's
     * `highlight-source-x`, file extensions) with varied spellings; normalize first.
     */
    @Test
    fun languageNamesAreNormalised() {
        assertEquals("kotlin", Syntax.normalizeLanguage("kotlin"))
        assertEquals("kotlin", Syntax.normalizeLanguage("kt"))
        assertEquals("kotlin", Syntax.normalizeLanguage("KTS"))
        assertEquals("javascript", Syntax.normalizeLanguage("js"))
        assertEquals("javascript", Syntax.normalizeLanguage("jsx"))
        assertEquals("typescript", Syntax.normalizeLanguage("ts"))
        assertEquals("shell", Syntax.normalizeLanguage("bash"))
        assertEquals("shell", Syntax.normalizeLanguage("shell-session"))
        assertEquals("shell", Syntax.normalizeLanguage("console"))
        assertEquals("csharp", Syntax.normalizeLanguage("cs"))
        assertEquals("cpp", Syntax.normalizeLanguage("c++"))
        assertEquals("markup", Syntax.normalizeLanguage("xml"))
        assertEquals("go", Syntax.normalizeLanguage("golang"))
        assertEquals(null, Syntax.normalizeLanguage("brainfuck"))
        assertEquals(null, Syntax.normalizeLanguage(""))
        assertEquals(null, Syntax.normalizeLanguage(null))
        // A normalized name must actually be highlightable.
        assertTrue(texts("const x = 1", Syntax.normalizeLanguage("js"), TokenKind.KEYWORD).contains("const"))
    }
}
