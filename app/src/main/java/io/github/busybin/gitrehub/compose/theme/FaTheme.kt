package io.github.busybin.gitrehub.compose.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.compose.common.LocalTokens
import io.github.busybin.gitrehub.compose.common.UiTokens

/**
 * Code palette of the current theme (used by reading code blocks and the file viewer). Separate from
 * Material's colours because Material has no keyword/string/comment colours.
 */
val LocalCodePalette = compositionLocalOf { (ThemeId.default.spec ?: error("default theme needs base tones")).lightCode }

/** Same font file the legacy UI uses, so both share the app's look. */
private val FaTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

/**
 * Root theme of the modern UI.
 *
 * Colour comes from the selected [ThemeId], a curated hand-written pair of schemes (light + dark)
 * with its own syntax palette, or from Material You when "wallpaper-following" is selected
 * (Android 12+).
 *
 * [LocalCodePalette] is provided here so the reading surface highlights code with the same theme
 * instead of the old hardcoded colours.
 */
@Composable
fun FaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val theme by ThemeState.theme.collectAsStateWithLifecycle()
    val fontScale by ThemeState.fontScale.collectAsStateWithLifecycle()
    val density by ThemeState.density.collectAsStateWithLifecycle()

    // The modern UI is dark-only (2026-10-18): the light/system switch is gone.
    val dark = true

    val dynamic = theme == ThemeId.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme: ColorScheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        theme.spec != null -> theme.spec!!.let { if (dark) it.dark.scheme(true) else it.light.scheme(false) }
        // Only wallpaper-following has no base tones and is handled above; this is the fallback.
        else -> ThemeId.default.spec!!.let { if (dark) it.dark.scheme(true) else it.light.scheme(false) }
    }

    val codePalette = remember(theme, dark, dynamic, scheme) {
        val base = (theme.spec ?: ThemeId.default.spec)!!.let { if (dark) it.darkCode else it.lightCode }
        if (dynamic) {
            // Wallpaper-following: background/text/comment follow the system, syntax tokens stay GitHub's
            // (they read well on any background, and Material has no string/keyword colours).
            base.copy(
                background = scheme.surfaceContainerHighest,
                plain = scheme.onSurface,
                comment = scheme.onSurfaceVariant,
            )
        } else {
            base
        }
    }

    // User adjustable font scale, applied to every Compose text.
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density, fontScale),
        LocalTokens provides UiTokens(scale = density),
        LocalCodePalette provides codePalette,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = FaTypography,
            content = content,
        )
    }
}
