package io.github.busybin.gitrehub.compose.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * User-visible text: either a string resource (localizable) or a raw string (server/exception text
 * that cannot be translated). ViewModels and pure logic have no Context, so they produce [UiText]
 * and Compose renders it with [render].
 */
sealed interface UiText {

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val text: String) : UiText

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())

        fun raw(text: String): UiText = Raw(text)
    }
}

/** Render it to the string actually shown, in Compose. */
@Composable
fun UiText.render(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Raw -> text
}
