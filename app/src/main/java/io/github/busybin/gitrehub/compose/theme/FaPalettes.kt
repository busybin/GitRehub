package io.github.busybin.gitrehub.compose.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import io.github.busybin.gitrehub.R

/**
 * Switchable named colour themes.
 *
 * Each theme is a hand-written set of base tones (background / surface / variant / on-surface /
 * on-variant / primary / outline, light and dark); the remaining Material roles are derived from
 * them by [scheme], since writing 400 values by hand is unnecessary and less consistent. Each theme
 * also carries its own code palette ([CodePalette]).
 *
 * Values come from each theme's official palette (GitHub Primer, Atom One, Dracula/Alucard, Nord,
 * Catppuccin Latte/Mocha, Solarized).
 */
enum class ThemeId(@StringRes val labelRes: Int, @StringRes val blurbRes: Int) {
    WALLPAPER(R.string.m3_theme_wallpaper, R.string.m3_theme_wallpaper_desc),
    GITHUB(R.string.m3_theme_github, R.string.m3_theme_github_desc),
    ONE_DARK(R.string.m3_theme_onedark, R.string.m3_theme_onedark_desc),
    DRACULA(R.string.m3_theme_dracula, R.string.m3_theme_dracula_desc),
    NORD(R.string.m3_theme_nord, R.string.m3_theme_nord_desc),
    CATPPUCCIN(R.string.m3_theme_catppuccin, R.string.m3_theme_catppuccin_desc),
    SOLARIZED(R.string.m3_theme_solarized, R.string.m3_theme_solarized_desc),
    GRUVBOX(R.string.m3_theme_gruvbox, R.string.m3_theme_gruvbox_desc),
    TOKYO_NIGHT(R.string.m3_theme_tokyonight, R.string.m3_theme_tokyonight_desc),
    ;

    companion object {
        val default: ThemeId = GITHUB

        /** Unknown names (including values from old versions) fall back to the default theme. */
        fun from(name: String?): ThemeId = entries.firstOrNull { it.name == name } ?: default

        /** Themes selectable in settings (excludes wallpaper-following, which needs system support). */
        val selectable: List<ThemeId> get() = entries.toList()
    }
}

/**
 * Code highlighting palette: light and dark per theme. Not derived from [ColorScheme], because
 * readability comes from the relationships between the colours (keywords must pop, comments recede,
 * strings stay recognizable) — designed, not computed.
 */
data class CodePalette(
    val background: Color,
    val plain: Color,
    val comment: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val function: Color,
    val type: Color,
) {
    /** @ColorInt for the legacy pipeline ([io.github.busybin.gitrehub.provider.timeline.MarkdownPalette]). */
    val backgroundArgb: Int get() = background.toArgb()
    val plainArgb: Int get() = plain.toArgb()
    val commentArgb: Int get() = comment.toArgb()
    val keywordArgb: Int get() = keyword.toArgb()
    val stringArgb: Int get() = string.toArgb()
    val numberArgb: Int get() = number.toArgb()
    val functionArgb: Int get() = function.toArgb()
    val typeArgb: Int get() = type.toArgb()
}

/** One screen's base tones: only the ones that must be exact; the rest is derived by [scheme]. */
internal class Tones(
    val background: Color,
    val surface: Color,
    /** M3 surfaceVariant: the secondary surface (chips, inline code, secondary cards). */
    val variant: Color,
    val onSurface: Color,
    val onVariant: Color,
    val primary: Color,
    val outline: Color,
)

internal class FaSpec(
    val light: Tones,
    val dark: Tones,
    val lightCode: CodePalette,
    val darkCode: CodePalette,
)

private val specCache = HashMap<ThemeId, FaSpec?>()

/**
 * Theme base tones. Each theme is built once (the table is not small and composition asks for it
 * repeatedly); read on the main thread only, so a plain HashMap is enough.
 */
internal val ThemeId.spec: FaSpec?
    get() = specCache.getOrPut(this) { buildSpec() }

private fun ThemeId.buildSpec(): FaSpec? = when (this) {
        // Wallpaper-following takes the whole scheme from the system, so there are no base tones.
        ThemeId.WALLPAPER -> null

        ThemeId.GITHUB -> FaSpec(
            light = Tones(
                background = Color(0xFFFFFFFF),
                surface = Color(0xFFFFFFFF),
                variant = Color(0xFFF6F8FA),
                onSurface = Color(0xFF1F2328),
                onVariant = Color(0xFF59636E),
                primary = Color(0xFF0969DA),
                outline = Color(0xFFD1D9E0),
            ),
            dark = Tones(
                background = Color(0xFF0D1117),
                surface = Color(0xFF0D1117),
                variant = Color(0xFF161B22),
                onSurface = Color(0xFFE6EDF3),
                onVariant = Color(0xFF9198A1),
                primary = Color(0xFF4493F8),
                outline = Color(0xFF30363D),
            ),
            lightCode = CodePalette(
                background = Color(0xFFF6F8FA),
                plain = Color(0xFF1F2328),
                comment = Color(0xFF59636E),
                keyword = Color(0xFFCF222E),
                string = Color(0xFF0A3069),
                number = Color(0xFF0550AE),
                function = Color(0xFF8250DF),
                type = Color(0xFF953800),
            ),
            darkCode = CodePalette(
                background = Color(0xFF161B22),
                plain = Color(0xFFE6EDF3),
                comment = Color(0xFF8B949E),
                keyword = Color(0xFFFF7B72),
                string = Color(0xFFA5D6FF),
                number = Color(0xFF79C0FF),
                function = Color(0xFFD2A8FF),
                type = Color(0xFFFFA657),
            ),
        )

        ThemeId.ONE_DARK -> FaSpec(
            light = Tones( // One Light
                background = Color(0xFFFAFAFA),
                surface = Color(0xFFFFFFFF),
                variant = Color(0xFFEAEAEB),
                onSurface = Color(0xFF383A42),
                onVariant = Color(0xFF696C77),
                primary = Color(0xFF4078F2),
                outline = Color(0xFFD4D4D6),
            ),
            dark = Tones( // One Dark
                background = Color(0xFF282C34),
                surface = Color(0xFF282C34),
                variant = Color(0xFF21252B),
                onSurface = Color(0xFFABB2BF),
                onVariant = Color(0xFF8B92A0),
                primary = Color(0xFF61AFEF),
                outline = Color(0xFF3E4451),
            ),
            lightCode = CodePalette(
                background = Color(0xFFF0F0F1),
                plain = Color(0xFF383A42),
                comment = Color(0xFFA0A1A7),
                keyword = Color(0xFFA626A4),
                string = Color(0xFF50A14F),
                number = Color(0xFF986801),
                function = Color(0xFF4078F2),
                type = Color(0xFFC18401),
            ),
            darkCode = CodePalette(
                background = Color(0xFF21252B),
                plain = Color(0xFFABB2BF),
                comment = Color(0xFF5C6370),
                keyword = Color(0xFFC678DD),
                string = Color(0xFF98C379),
                number = Color(0xFFD19A66),
                function = Color(0xFF61AFEF),
                type = Color(0xFFE5C07B),
            ),
        )

        ThemeId.DRACULA -> FaSpec(
            light = Tones( // Alucard
                background = Color(0xFFFFFBEB),
                surface = Color(0xFFFFFBEB),
                variant = Color(0xFFF3EFDC),
                onSurface = Color(0xFF1F1F1F),
                onVariant = Color(0xFF6C664B),
                primary = Color(0xFF644AC9),
                outline = Color(0xFFCFCFDE),
            ),
            dark = Tones(
                background = Color(0xFF282A36),
                surface = Color(0xFF282A36),
                variant = Color(0xFF343746),
                onSurface = Color(0xFFF8F8F2),
                onVariant = Color(0xFFA6ACD6),
                primary = Color(0xFFBD93F9),
                outline = Color(0xFF44475A),
            ),
            lightCode = CodePalette(
                background = Color(0xFFF3EFDC),
                plain = Color(0xFF1F1F1F),
                comment = Color(0xFF6C664B),
                keyword = Color(0xFF644AC9),
                string = Color(0xFF14710A),
                number = Color(0xFFA34D14),
                function = Color(0xFF036A96),
                type = Color(0xFF846E15),
            ),
            darkCode = CodePalette(
                background = Color(0xFF343746),
                plain = Color(0xFFF8F8F2),
                comment = Color(0xFF6272A4),
                keyword = Color(0xFFFF79C6),
                string = Color(0xFFF1FA8C),
                number = Color(0xFFBD93F9),
                function = Color(0xFF50FA7B),
                type = Color(0xFF8BE9FD),
            ),
        )

        ThemeId.NORD -> FaSpec(
            light = Tones( // Snow Storm
                background = Color(0xFFECEFF4),
                surface = Color(0xFFECEFF4),
                variant = Color(0xFFE5E9F0),
                onSurface = Color(0xFF2E3440),
                onVariant = Color(0xFF4C566A),
                primary = Color(0xFF5E81AC),
                outline = Color(0xFFD8DEE9),
            ),
            dark = Tones( // Polar Night
                background = Color(0xFF2E3440),
                surface = Color(0xFF2E3440),
                variant = Color(0xFF3B4252),
                onSurface = Color(0xFFECEFF4),
                onVariant = Color(0xFFD8DEE9),
                primary = Color(0xFF88C0D0),
                outline = Color(0xFF4C566A),
            ),
            lightCode = CodePalette(
                background = Color(0xFFE5E9F0),
                plain = Color(0xFF2E3440),
                comment = Color(0xFF7B88A1),
                keyword = Color(0xFF5E81AC),
                string = Color(0xFF4F894C),
                number = Color(0xFFA16A9E),
                function = Color(0xFF3B6EA5),
                type = Color(0xFF9A7B2E),
            ),
            darkCode = CodePalette(
                background = Color(0xFF3B4252),
                plain = Color(0xFFD8DEE9),
                comment = Color(0xFF616E88),
                keyword = Color(0xFF81A1C1),
                string = Color(0xFFA3BE8C),
                number = Color(0xFFB48EAD),
                function = Color(0xFF88C0D0),
                type = Color(0xFFEBCB8B),
            ),
        )

        ThemeId.CATPPUCCIN -> FaSpec(
            light = Tones( // Latte
                background = Color(0xFFEFF1F5),
                surface = Color(0xFFEFF1F5),
                variant = Color(0xFFE6E9EF),
                onSurface = Color(0xFF4C4F69),
                onVariant = Color(0xFF6C6F85),
                primary = Color(0xFF1E66F5),
                outline = Color(0xFFBCC0CC),
            ),
            dark = Tones( // Mocha
                background = Color(0xFF1E1E2E),
                surface = Color(0xFF1E1E2E),
                variant = Color(0xFF181825),
                onSurface = Color(0xFFCDD6F4),
                onVariant = Color(0xFFA6ADC8),
                primary = Color(0xFF89B4FA),
                outline = Color(0xFF45475A),
            ),
            lightCode = CodePalette(
                background = Color(0xFFE6E9EF),
                plain = Color(0xFF4C4F69),
                comment = Color(0xFF8C8FA1),
                keyword = Color(0xFF8839EF),
                string = Color(0xFF40A02B),
                number = Color(0xFFFE640B),
                function = Color(0xFF1E66F5),
                type = Color(0xFFDF8E1D),
            ),
            darkCode = CodePalette(
                background = Color(0xFF181825),
                plain = Color(0xFFCDD6F4),
                comment = Color(0xFF7F849C),
                keyword = Color(0xFFCBA6F7),
                string = Color(0xFFA6E3A1),
                number = Color(0xFFFAB387),
                function = Color(0xFF89B4FA),
                type = Color(0xFFF9E2AF),
            ),
        )

        ThemeId.SOLARIZED -> FaSpec(
            light = Tones(
                background = Color(0xFFFDF6E3),
                surface = Color(0xFFFDF6E3),
                variant = Color(0xFFEEE8D5),
                onSurface = Color(0xFF657B83),
                onVariant = Color(0xFF586E75),
                primary = Color(0xFF268BD2),
                outline = Color(0xFFDDD6C1),
            ),
            dark = Tones(
                background = Color(0xFF002B36),
                surface = Color(0xFF002B36),
                variant = Color(0xFF073642),
                onSurface = Color(0xFF93A1A1),
                onVariant = Color(0xFF839496),
                primary = Color(0xFF6CC0E8),
                outline = Color(0xFF0F4A57),
            ),
            lightCode = CodePalette(
                background = Color(0xFFEEE8D5),
                plain = Color(0xFF657B83),
                comment = Color(0xFF93A1A1),
                keyword = Color(0xFF859900),
                string = Color(0xFF2AA198),
                number = Color(0xFFD33682),
                function = Color(0xFF268BD2),
                type = Color(0xFFB58900),
            ),
            darkCode = CodePalette(
                background = Color(0xFF073642),
                plain = Color(0xFF839496),
                comment = Color(0xFF586E75),
                keyword = Color(0xFF859900),
                string = Color(0xFF2AA198),
                number = Color(0xFFD33682),
                function = Color(0xFF268BD2),
                type = Color(0xFFB58900),
            ),
        )

        ThemeId.GRUVBOX -> FaSpec(
            light = Tones( // Gruvbox Light
                background = Color(0xFFFBF1C7),
                surface = Color(0xFFFBF1C7),
                variant = Color(0xFFEBDBB2),
                onSurface = Color(0xFF3C3836),
                onVariant = Color(0xFF7C6F64),
                primary = Color(0xFF076678),
                outline = Color(0xFFD5C4A1),
            ),
            dark = Tones( // Gruvbox Dark
                background = Color(0xFF282828),
                surface = Color(0xFF282828),
                variant = Color(0xFF3C3836),
                onSurface = Color(0xFFEBDBB2),
                onVariant = Color(0xFFA89984),
                primary = Color(0xFF83A598),
                outline = Color(0xFF504945),
            ),
            lightCode = CodePalette(
                background = Color(0xFFEBDBB2),
                plain = Color(0xFF3C3836),
                comment = Color(0xFF928374),
                keyword = Color(0xFF9D0006),
                string = Color(0xFF79740E),
                number = Color(0xFF8F3F71),
                function = Color(0xFF427B58),
                type = Color(0xFFB57614),
            ),
            darkCode = CodePalette(
                background = Color(0xFF32302F),
                plain = Color(0xFFEBDBB2),
                comment = Color(0xFF928374),
                keyword = Color(0xFFFB4934),
                string = Color(0xFFB8BB26),
                number = Color(0xFFD3869B),
                function = Color(0xFF8EC07C),
                type = Color(0xFFFABD2F),
            ),
        )

        ThemeId.TOKYO_NIGHT -> FaSpec(
            light = Tones( // Tokyo Night Day
                background = Color(0xFFE1E2E7),
                surface = Color(0xFFE1E2E7),
                variant = Color(0xFFD0D5E3),
                onSurface = Color(0xFF3760BF),
                onVariant = Color(0xFF6172B0),
                primary = Color(0xFF2E7DE9),
                outline = Color(0xFFA8AECB),
            ),
            dark = Tones( // Tokyo Night (Storm)
                background = Color(0xFF1A1B26),
                surface = Color(0xFF1A1B26),
                variant = Color(0xFF24283B),
                onSurface = Color(0xFFC0CAF5),
                onVariant = Color(0xFFA9B1D6),
                primary = Color(0xFF7AA2F7),
                outline = Color(0xFF3B4261),
            ),
            lightCode = CodePalette(
                background = Color(0xFFD0D5E3),
                plain = Color(0xFF3760BF),
                comment = Color(0xFF9699A3),
                keyword = Color(0xFF9854F1),
                string = Color(0xFF587539),
                number = Color(0xFFB15C00),
                function = Color(0xFF2E7DE9),
                type = Color(0xFF007197),
            ),
            darkCode = CodePalette(
                background = Color(0xFF1F2335),
                plain = Color(0xFFC0CAF5),
                comment = Color(0xFF565F89),
                keyword = Color(0xFFBB9AF7),
                string = Color(0xFF9ECE6A),
                number = Color(0xFFFF9E64),
                function = Color(0xFF7AA2F7),
                type = Color(0xFF2AC3DE),
            ),
        )
    }

/**
 * Base tones to a full Material 3 scheme. Derivation rules: `*Container` mixes primary into the
 * surface (more on dark); `surfaceContainer*` mixes the text colour in, more at each step, with the
 * dark `Lowest` darkened further and the light `Lowest` equal to the background; `onPrimary` picks
 * dark/light from the primary's luminance; errors use the M3 baseline red.
 */
internal fun Tones.scheme(dark: Boolean): ColorScheme {
    fun mix(a: Color, b: Color, f: Float) = lerp(a, b, f)

    val containerAmount = if (dark) 0.30f else 0.16f
    val pan = if (dark) 0.30f else 0.14f
    val primaryContainer = mix(surface, primary, containerAmount)
    val onPrimaryContainer = mix(onSurface, primary, pan)
    val secondary = mix(onSurface, primary, 0.45f)
    val tertiary = mix(onSurface, primary, 0.28f)
    val onPrimary = if (primary.luminance() > 0.45f) mix(background, Color.Black, 0.35f) else Color.White

    val containerLow = mix(surface, onSurface, if (dark) 0.03f else 0.03f)
    val container = mix(surface, onSurface, if (dark) 0.05f else 0.05f)
    val containerHigh = mix(surface, onSurface, if (dark) 0.07f else 0.08f)
    val containerHighest = mix(surface, onSurface, if (dark) 0.10f else 0.11f)
    val containerLowest = if (dark) mix(background, Color.Black, 0.28f) else background

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = mix(primary, surface, 0.35f),
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = mix(surface, primary, 0.20f),
            onSecondaryContainer = mix(onSurface, primary, 0.20f),
            tertiary = tertiary,
            onTertiary = onPrimary,
            tertiaryContainer = mix(surface, primary, 0.14f),
            onTertiaryContainer = mix(onSurface, primary, 0.14f),
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = variant,
            onSurfaceVariant = onVariant,
            surfaceTint = primary,
            surfaceDim = containerLowest,
            surfaceBright = containerHighest,
            surfaceContainerLowest = containerLowest,
            surfaceContainerLow = containerLow,
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHighest,
            inverseSurface = onSurface,
            inverseOnSurface = background,
            outline = outline,
            outlineVariant = mix(outline, surface, 0.45f),
            scrim = Color.Black,
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = mix(primary, onSurface, 0.30f),
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = mix(surface, primary, 0.10f),
            onSecondaryContainer = mix(onSurface, primary, 0.10f),
            tertiary = tertiary,
            onTertiary = onPrimary,
            tertiaryContainer = mix(surface, primary, 0.07f),
            onTertiaryContainer = mix(onSurface, primary, 0.07f),
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = variant,
            onSurfaceVariant = onVariant,
            surfaceTint = primary,
            surfaceDim = container,
            surfaceBright = background,
            surfaceContainerLowest = containerLowest,
            surfaceContainerLow = containerLow,
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHighest,
            inverseSurface = onSurface,
            inverseOnSurface = background,
            outline = outline,
            outlineVariant = mix(outline, surface, 0.45f),
            scrim = Color.Black,
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    }
}
