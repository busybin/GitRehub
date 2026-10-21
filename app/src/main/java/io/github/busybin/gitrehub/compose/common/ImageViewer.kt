package io.github.busybin.gitrehub.compose.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.helper.BrowserLauncher

/**
 * Fullscreen viewer opened by tapping an image in body text (README / issue / release notes).
 * Pinch to zoom and drag to pan; on load failure it shows a message plus "open in browser".
 */
@Composable
fun ImageViewerDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
        ) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.m3_image_failed),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            // Reset the pan when zooming back to 1x, otherwise the image drifts off screen.
                            offset = if (scale <= 1f) Offset.Zero else offset + pan
                        }
                    },
            )
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.m3_close), tint = Color.White)
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                IconButton(onClick = { BrowserLauncher.open(context, url) }) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.m3_open_in_browser),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
