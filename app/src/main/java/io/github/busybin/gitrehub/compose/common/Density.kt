package io.github.busybin.gitrehub.compose.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout knobs shared by the list screens.
 *
 * [scale] is the user's "interface density": it multiplies every padding/spacing/avatar the screens
 * take from here, so moving the slider visibly tightens or loosens the whole app (the old boolean
 * comfortable/compact barely changed anything).
 *
 * It is provided once by `FaTheme` (see `LocalTokens`) and read by the screens, so a setting change
 * re-composes everything live.
 */
data class UiTokens(val scale: Float = 1f) {

    /** Compact-ish sizing (smaller rounding, secondary lines dropped) below the default density. */
    val compact: Boolean get() = scale < 0.95f

    private fun d(base: Float): Dp = (base * scale).dp

    val cardPadding: Dp get() = d(12f)
    val itemSpacing: Dp get() = d(8f)
    val listPadding: Dp get() = d(12f)
    val avatarSize: Dp get() = d(if (compact) 30f else 38f)
    val badgeSize: Dp get() = d(if (compact) 24f else 30f)
    val cornerRadius: Dp get() = d(if (compact) 10f else 16f)
    val cardShape: RoundedCornerShape get() = RoundedCornerShape(cornerRadius)

    /** Secondary lines (author of the last commit, descriptions...) are dropped when very compact. */
    val showSecondaryLines: Boolean get() = scale >= 0.85f
}

/** Density choices for the settings slider (0.80 … 1.20). */
object Density {
    const val MIN = 0.80f
    const val MAX = 1.20f
    const val DEFAULT = 1.0f
    fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)
}

val LocalTokens = compositionLocalOf { UiTokens() }
