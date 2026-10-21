package io.github.busybin.gitrehub.compose.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Star / fork / watch / follow share this: outlined and plain while inactive, filled in the accent
 * colour once the action is done, and Material's greyed-out colours while disabled.
 */
@Composable
fun ToggleActionButton(
    active: Boolean,
    enabled: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
    if (active) {
        Button(onClick = onClick, enabled = enabled) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { content() }
    }
}
