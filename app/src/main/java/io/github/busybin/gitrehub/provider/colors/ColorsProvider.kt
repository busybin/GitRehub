package io.github.busybin.gitrehub.provider.colors

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import io.github.busybin.gitrehub.App
import io.github.busybin.gitrehub.helper.InputHelper
import io.github.busybin.gitrehub.helper.rendering.ColorGenerator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One entry of `assets/colors.json`: the colour GitHub uses for a language. */
@Serializable
data class LanguageColor(
    val color: String? = null,
    val url: String? = null,
)

object ColorsProvider {
    const val DEFAULT_LANG = "All Languages"

    private val POPULAR_LANG = listOf(
        "Python", "JavaScript", "Java", "TypeScript",
        "Go", "C++", "Ruby", "PHP",
        "C#", "C", "Kotlin", "Rust",
        "Lua", "CSS", "Dart", "Swift",
    )
    private val colors: MutableMap<String, LanguageColor> = LinkedHashMap()

    @JvmStatic
    fun load() {
        if (colors.isNotEmpty()) return
        runCatching {
            val json = Json { ignoreUnknownKeys = true }
            App.getInstance().assets.open("colors.json").bufferedReader().use { reader ->
                colors.putAll(json.decodeFromString(reader.readText()))
            }
        }
    }

    fun languages(): List<String> = listOf(DEFAULT_LANG) + POPULAR_LANG + colors.keys

    fun color(lang: String): LanguageColor? = colors[lang]

    @JvmStatic
    @ColorInt
    fun getColorAsColor(lang: String, context: Context): Int {
        val color = color(lang)
        var langColor = ColorGenerator.getColor(context, lang)
        if (color != null && !InputHelper.isEmpty(color.color)) {
            try {
                langColor = Color.parseColor(color.color)
            } catch (ignored: Exception) {
            }
        }
        return langColor
    }
}
