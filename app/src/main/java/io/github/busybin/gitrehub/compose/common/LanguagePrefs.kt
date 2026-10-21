package io.github.busybin.gitrehub.compose.common

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.helper.PrefHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * In-app language selection.
 *
 * The modern UI strings exist in eight locales (`values/` plus zh-rCN, es, fr, ar, pt-rBR, ru, ja),
 * so the options are "follow system" plus those eight. Language names are written in their own
 * language; picking one rebuilds the UI so the new `attachBaseContext` takes effect — swapping
 * `LocalContext` via CompositionLocalProvider does not affect `stringResource`, which reads
 * `LocalResources` provided by the View layer.
 */
object LanguagePrefs {

    private const val KEY = "m3_language"

    /** `""` means follow the system; otherwise a BCP-47 language tag. */
    private val _tag = MutableStateFlow(PrefHelper.getString(KEY).orEmpty())
    val tag: StateFlow<String> = _tag.asStateFlow()

    /** Selectable options in display order (by number of speakers). */
    val options: List<String> =
        listOf("", "en", "zh", "es", "fr", "ar", "pt-BR", "ru", "ja")

    fun set(tag: String) {
        // "Follow system" is the empty tag and must really delete the key: PrefHelper.putAny treats
        // an empty value as "do not store", so only the in-memory flow would change and a cold
        // start would read the old tag back.
        if (tag.isBlank()) {
            PrefHelper.clearKey<String>(KEY)
        } else {
            PrefHelper.putAny(KEY, tag)
        }
        _tag.value = tag
    }

    @StringRes
    fun labelRes(tag: String): Int = when (tag) {
        "en" -> R.string.m3_lang_en
        "zh" -> R.string.m3_lang_zh
        "es" -> R.string.m3_lang_es
        "fr" -> R.string.m3_lang_fr
        "ar" -> R.string.m3_lang_ar
        "pt-BR" -> R.string.m3_lang_pt
        "ru" -> R.string.m3_lang_ru
        "ja" -> R.string.m3_lang_ja
        else -> R.string.m3_lang_system
    }

    /** Locale to use; `null` means follow the system and do not override. */
    fun locale(tag: String = _tag.value): Locale? =
        tag.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it) }

    /** Wrap [base] into a Context using the selected locale, for the Activity's `attachBaseContext`. */
    fun wrap(base: Context): Context {
        val locale = locale() ?: return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}
