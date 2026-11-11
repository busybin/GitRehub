package io.github.busybin.gitrehub.compose.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout knobs shared by every screen.
 *
 * [scale] is the user's "interface density" (0.80 … 1.20): it multiplies every size taken from
 * here, so moving the slider tightens or loosens the whole app. Base values are the 1.0 values and
 * all of them sit on the 4dp grid, so the rhythm survives at any density.
 *
 * Radii nest: [cornerRadius] (cards) = [innerRadius] (chips, inner blocks) + the 8dp padding used
 * between them.
 */
data class UiTokens(val scale: Float = 1f) {

    /** Compact-ish sizing (smaller rounding, secondary lines dropped) below the default density. */
    val compact: Boolean get() = scale < 0.95f

    private fun d(base: Float): Dp = (base * scale).dp

    // spacing scale
    val space1: Dp get() = d(4f)
    val space2: Dp get() = d(8f)
    val space3: Dp get() = d(12f)
    val space4: Dp get() = d(16f)
    val space6: Dp get() = d(24f)

    /** Page edge padding for scrolling content. */
    val pagePadding: Dp get() = d(16f)
    val cardPadding: Dp get() = d(16f)
    val itemSpacing: Dp get() = d(8f)
    val listPadding: Dp get() = d(16f)

    /** Minimum height of one list row (two-line rows grow past it). */
    val rowMinHeight: Dp get() = d(if (compact) 44f else 56f)

    /** Height of a single-line, code-density row (file tree, commits, branches). */
    val rowTight: Dp get() = d(if (compact) 40f else 48f)

    val avatarSize: Dp get() = d(if (compact) 32f else 36f)
    val avatarSmall: Dp get() = d(20f)
    val badgeSize: Dp get() = d(if (compact) 24f else 28f)

    /** One icon size everywhere in the chrome; [iconSmall] only inside dense meta rows. */
    val iconSize: Dp get() = d(18f)
    val iconSmall: Dp get() = d(14f)

    val cornerRadius: Dp get() = d(if (compact) 10f else 12f)
    val cardShape: RoundedCornerShape get() = RoundedCornerShape(cornerRadius)
    val innerRadius: Dp get() = d(8f)
    val innerShape: RoundedCornerShape get() = RoundedCornerShape(innerRadius)

    /** Secondary lines (descriptions, commit author...) are dropped when very compact. */
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
