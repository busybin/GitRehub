package io.github.busybin.gitrehub.compose.theme

import android.os.Build
import io.github.busybin.gitrehub.helper.PrefHelper
import io.github.busybin.gitrehub.compose.common.Density
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted settings of the modern (Compose) UI.
 *
 * Deliberately stored through [PrefHelper] (MMKV, keys prefixed with their type) so the values are
 * picked up by the legacy backup/restore code, which walks every `"<type>@<key>"` entry.
 */
object ThemePrefs {
    internal const val KEY_THEME = "m3_theme_id"
    internal const val KEY_FONT_SCALE = "m3_font_scale"
    internal const val KEY_COMPACT = "m3_compact"
    internal const val KEY_DENSITY = "m3_density_scale"

    /** Keys from the old version (seed colour + dynamic colour switch), used for migration. */
    internal const val KEY_LEGACY_DYNAMIC = "m3_dynamic_color"
    internal const val KEY_LEGACY_SEED = "m3_seed_color"

    /** Font scale slider range (1.0 = system default). 2026-10-18: was four chips, now a slider. */
    val fontScaleMin: Float = 0.80f
    val fontScaleMax: Float = 1.40f

    val defaultFontScale: Float = 1.0f

    /** Material You dynamic colour is only offered on Android 12+. */
    val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * Single source of truth for the Compose theme so the settings screen can change it live.
 * The modern UI is dark only (the theme-mode switch was removed), so there is no mode here.
 */
object ThemeState {
    private val _theme = MutableStateFlow(readTheme())
    val theme: StateFlow<ThemeId> = _theme.asStateFlow()

    private val _fontScale = MutableStateFlow(readFontScale())
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    /** Compact information density (smaller cards, tighter spacing, secondary lines dropped). */
    private val _compact = MutableStateFlow(readCompact())
    val compact: StateFlow<Boolean> = _compact.asStateFlow()

    /** Interface density (0.8 … 1.2): scales paddings and spacing uniformly. */
    private val _density = MutableStateFlow(readDensity())
    val density: StateFlow<Float> = _density.asStateFlow()

    fun setTheme(value: ThemeId) {        PrefHelper.putAny(ThemePrefs.KEY_THEME, value.name)
        _theme.value = value
    }

    fun setFontScale(scale: Float) {
        previewFontScale(scale)
        PrefHelper.putAny(ThemePrefs.KEY_FONT_SCALE, _fontScale.value)
    }

    /** While dragging, update the in-memory value only (do not write MMKV every frame); [setFontScale] persists on release. */
    fun previewFontScale(scale: Float) {
        _fontScale.value = scale.coerceIn(ThemePrefs.fontScaleMin, ThemePrefs.fontScaleMax)
    }

    fun setCompact(compact: Boolean) {
        PrefHelper.putAny(ThemePrefs.KEY_COMPACT, compact)
        _compact.value = compact
    }

    fun setDensity(value: Float) {
        previewDensity(value)
        PrefHelper.putAny(ThemePrefs.KEY_DENSITY, _density.value)
    }

    fun previewDensity(value: Float) {
        _density.value = Density.clamp(value)
    }

    private fun readCompact(): Boolean =
        PrefHelper.isExist<Boolean>(ThemePrefs.KEY_COMPACT) &&
            PrefHelper.getBoolean(ThemePrefs.KEY_COMPACT)

    /** New key first; when upgrading from the old boolean "compact" switch, derive once. */
    private fun readDensity(): Float {
        val stored = PrefHelper.getFloat(ThemePrefs.KEY_DENSITY)
        if (stored > 0.5f && stored < 2f) return Density.clamp(stored)
        return if (readCompact()) 0.82f else Density.DEFAULT
    }

    private fun readFontScale(): Float {
        val stored = PrefHelper.getFloat(ThemePrefs.KEY_FONT_SCALE)
        return if (stored > 0.5f && stored < 2f) stored else ThemePrefs.defaultFontScale
    }

    /**
     * Selected theme. On first run (or when upgrading from the seed-colour version): if dynamic
     * colour was on, follow the wallpaper, otherwise the default GitHub.
     */
    private fun readTheme(): ThemeId {
        val stored = PrefHelper.getString(ThemePrefs.KEY_THEME)
        if (!stored.isNullOrBlank()) return ThemeId.from(stored)
        val legacyDynamic = PrefHelper.isExist<Boolean>(ThemePrefs.KEY_LEGACY_DYNAMIC) &&
            PrefHelper.getBoolean(ThemePrefs.KEY_LEGACY_DYNAMIC)
        return if (legacyDynamic && ThemePrefs.supportsDynamicColor) ThemeId.WALLPAPER else ThemeId.default
    }
}
