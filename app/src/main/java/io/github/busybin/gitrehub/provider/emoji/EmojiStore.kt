package io.github.busybin.gitrehub.provider.emoji

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * GitHub emoji aliases (`:rocket:`) mapped to their unicode glyphs.
 *
 * Only aliases are needed: markdown is rendered by converting an alias to unicode before it reaches
 * the text view, and nothing converts glyphs back.
 */
object EmojiStore {
    @Serializable
    private data class Entry(val emoji: String = "", val aliases: List<String> = emptyList())

    private val byAlias = ConcurrentHashMap<String, String>()
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context) {
        if (byAlias.isNotEmpty()) return
        runCatching {
            context.assets.open("emojis.json").bufferedReader().use { reader ->
                json.decodeFromString<List<Entry>>(reader.readText()).forEach { entry ->
                    entry.aliases.forEach { alias -> byAlias[alias] = entry.emoji }
                }
            }
        }
    }

    /** Accepts `rocket`, `:rocket:` and `:|type_6` style aliases. */
    fun unicode(alias: String?): String? {
        if (alias.isNullOrBlank()) return null
        val name = alias.trim().trim(':').substringBefore('|')
        return byAlias[name]
    }
}
