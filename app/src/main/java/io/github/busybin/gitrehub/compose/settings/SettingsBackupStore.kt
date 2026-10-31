package io.github.busybin.gitrehub.compose.settings

import io.github.busybin.gitrehub.compose.common.LanguagePrefs
import io.github.busybin.gitrehub.compose.common.NetworkPrefs
import io.github.busybin.gitrehub.compose.common.SavedPrefs
import io.github.busybin.gitrehub.compose.common.SavedReplies
import io.github.busybin.gitrehub.compose.common.SavedReply
import io.github.busybin.gitrehub.compose.common.SavedSearch
import io.github.busybin.gitrehub.compose.common.SwipeAction
import io.github.busybin.gitrehub.compose.common.SwipePrefs
import io.github.busybin.gitrehub.compose.theme.ThemeId
import io.github.busybin.gitrehub.compose.theme.ThemeState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Settings export / import as one JSON file: theme, type size, density, language, swipe actions,
 * saved searches, favourite repositories, canned replies and the image data saver.
 *
 * Deliberately excludes tokens/accounts and cache contents; this is for moving a setup to a new
 * phone, not a backup of credentials.
 */
@Serializable
data class SettingsBackup(
    val version: Int = 1,
    val theme: String = "",
    val fontScale: Float = 1f,
    val density: Float = 1f,
    val lineHeight: Float = 1f,
    val language: String = "",
    val swipeStart: String = "",
    val swipeEnd: String = "",
    val wifiOnlyImages: Boolean = false,
    val searches: List<SavedSearch> = emptyList(),
    val favorites: List<String> = emptyList(),
    val replies: List<SavedReply> = emptyList(),
)

object SettingsBackupStore {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun export(): String = json.encodeToString(
        SettingsBackup(
            theme = ThemeState.theme.value.name,
            fontScale = ThemeState.fontScale.value,
            density = ThemeState.density.value,
            lineHeight = ThemeState.lineHeight.value,
            language = LanguagePrefs.tag.value,
            swipeStart = SwipePrefs.start.value.name,
            swipeEnd = SwipePrefs.end.value.name,
            wifiOnlyImages = NetworkPrefs.wifiOnlyImages.value,
            searches = SavedPrefs.searches.value,
            favorites = SavedPrefs.favorites.value.toList(),
            replies = SavedReplies.items.value,
        )
    )

    /** Applies a backup; returns false on malformed JSON or an unknown version. */
    fun import(text: String): Boolean {
        val data = runCatching { json.decodeFromString<SettingsBackup>(text) }.getOrNull()
            ?: return false
        if (data.version != 1) return false
        ThemeState.setTheme(ThemeId.from(data.theme))
        ThemeState.setFontScale(data.fontScale)
        ThemeState.setDensity(data.density)
        ThemeState.setLineHeight(data.lineHeight)
        if (data.language.isNotBlank() && data.language in LanguagePrefs.options) {
            LanguagePrefs.set(data.language)
        }
        SwipePrefs.setStart(SwipeAction.from(data.swipeStart))
        SwipePrefs.setEnd(SwipeAction.from(data.swipeEnd))
        NetworkPrefs.setWifiOnlyImages(data.wifiOnlyImages)
        SavedPrefs.replaceAll(data.searches, data.favorites.toSet())
        SavedReplies.replaceAll(data.replies)
        return true
    }
}
