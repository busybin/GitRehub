package io.github.busybin.gitrehub.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Wide-screen check (tablet / landscape phone), used to decide whether to lay out two columns.
 *
 * 600dp: Material's `Medium` window class starts there and list + detail fit side by side.
 * `screenWidthDp` rather than `smallestScreenWidthDp`, so landscape phones get two columns too.
 */
@Composable
fun isWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp >= 600
