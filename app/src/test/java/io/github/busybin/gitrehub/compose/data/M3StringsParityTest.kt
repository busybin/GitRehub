package io.github.busybin.gitrehub.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Eight-locale parity guard for `m3_strings.xml` (reads the resource files from the repo).
 *
 * A missing key silently shows English in that locale (Android falls back per key); a placeholder
 * mismatch crashes at runtime (`String.format` throws IllegalFormatException).
 *
 * The Gradle unit-test working directory is the module dir (`app/`), so the relative path reaches
 * src/main/res.
 */
class M3StringsParityTest {

    private val locales = linkedMapOf(
        "en" to "values",
        "zh" to "values-zh-rCN",
        "es" to "values-es",
        "fr" to "values-fr",
        "ar" to "values-ar",
        "pt-BR" to "values-pt-rBR",
        "ru" to "values-ru",
        "ja" to "values-ja",
    )

    private fun resDir(): File {
        listOf("src/main/res", "app/src/main/res", "../app/src/main/res").forEach { relative ->
            val dir = File(relative)
            if (File(dir, "values/m3_strings.xml").isFile) return dir
        }
        throw AssertionError("find src/main/res failed, cwd=${File("").absolutePath}")
    }

    private fun parse(localeDir: String): Map<String, String> {
        val file = File(resDir(), "$localeDir/m3_strings.xml")
        assertTrue("missing $localeDir/m3_strings.xml", file.isFile)
        val pattern = Regex("<string\\s+name=\"([^\"]+)\"\\s*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        return pattern.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    /** `%1$s` / `%d` → keep only the type: `%s` / `%d` (the index number is not a difference). */
    private fun placeholders(value: String): Set<String> =
        Regex("%(\\d+\\$)?([a-zA-Z])").findAll(value).map { "%" + it.groupValues[2] }.toSet()

    @Test
    fun everyLocaleHasTheSameKeys() {
        val base = parse("values")
        assertTrue("default file looks empty", base.size > 500)
        locales.forEach { (locale, dir) ->
            val keys = parse(dir).keys
            assertEquals("$locale is missing keys", emptySet<String>(), base.keys - keys)
            assertEquals("$locale has extra keys", emptySet<String>(), keys - base.keys)
        }
    }

    @Test
    fun placeholdersMatchTheDefaultFile() {
        val base = parse("values")
        locales.forEach { (locale, dir) ->
            parse(dir).forEach { (key, value) ->
                val expected = base[key] ?: return@forEach
                assertEquals("$locale $key placeholder mismatch", placeholders(expected), placeholders(value))
            }
        }
    }
}
