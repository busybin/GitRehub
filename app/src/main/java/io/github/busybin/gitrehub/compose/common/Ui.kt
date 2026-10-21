package io.github.busybin.gitrehub.compose.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.helper.OfflineStatus
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Loading / error / content state used by every modern screen.
 */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Failure(val message: UiText, val cause: Throwable? = null) : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
}

/** Turn an exception into a user-readable message: known errors become a localizable [UiText.Res], others pass through as [UiText.Raw]. */
fun Throwable.toUiText(): UiText {
    val raw = message ?: return UiText.res(R.string.m3_unknown_error)
    return when {
        raw.contains("401") -> UiText.res(R.string.m3_error_401)
        raw.contains("403") -> UiText.res(R.string.m3_error_403)
        raw.contains("404") -> UiText.res(R.string.m3_error_404)
        // OkHttp returns 504 for "only-if-cached" requests when there is no cache.
        raw.contains("504") || raw.contains("only-if-cached") || raw.contains("Unsatisfiable") ->
            UiText.res(R.string.m3_error_offline)

        raw.contains("Unable to resolve host") || raw.contains("Failed to connect") ->
            UiText.res(R.string.m3_error_no_network)

        raw.contains("timeout", true) -> UiText.res(R.string.m3_error_timeout)
        else -> UiText.raw(raw)
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun FailureView(
    message: UiText,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message.render(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.m3_retry)) }
        }
    }
}

@Composable
fun EmptyView(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/**
 * Last resort when no browser could be launched: show the link so the user can copy it.
 * `BrowserLauncher` returns false instead of failing silently, and every caller pairs the two.
 */
@Composable
fun LinkCopyDialog(url: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m3_browser_open_failed_title)) },
        text = {
            Text(
                stringResource(R.string.m3_browser_open_failed_message, url),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(url))
                    onDismiss()
                },
            ) { Text(stringResource(R.string.m3_copy_link)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_close)) } },
    )
}

/**
 * Yes/no confirmation, used wherever an action cannot be taken back (logging out).
 *
 * @param destructive tints the confirm button with the error colour, so a destructive action does
 *   not look like every other button.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.m3_cancel)) } },
    )
}

/** Renders [state]: spinner while loading, retry-able error, otherwise [content]. */
@Composable
fun <T> AsyncContent(
    state: LoadState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    loading: (@Composable () -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is LoadState.Loading -> if (loading != null) loading() else LoadingView(modifier)
        is LoadState.Failure -> FailureView(state.message, modifier, onRetry)
        is LoadState.Ready -> content(state.value)
    }
}

/**
 * Placeholder rows shown while a list is loading.
 *
 * A bare centred spinner on an otherwise empty screen reads as "nothing is happening" — this makes
 * the wait legible and stops the layout from jumping when the real rows arrive.
 */
@Composable
fun SkeletonList(
    rows: Int = 6,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTokens.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(tokens.listPadding),
        verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing),
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(tokens.cardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(tokens.cardPadding),
            ) {
                Box(
                    Modifier
                        .size(tokens.avatarSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonBar(0.45f, alpha)
                    SkeletonBar(0.85f, alpha)
                    SkeletonBar(0.30f, alpha)
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float, alpha: Float) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

@Composable
fun Avatar(
    url: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val revisions by AvatarVersions.revisions.collectAsStateWithLifecycle()
    val version = revisions[url] ?: 0L
    // The URL does not change when the picture does; [AvatarVersions] bumps the revision after an
    // ETag check, and a different cache key is what makes Coil fetch the new bytes.
    LaunchedEffect(url) { AvatarVersions.check(url) }
    val model = remember(url, version, context) {
        if (url.isNullOrBlank()) {
            url
        } else {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey("$url#v=$version")
                .diskCacheKey("$url#v=$version")
                .build()
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                },
            )
        } else {
            Text(
                text = "?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Rounded chip-like label, used for languages, labels, states... */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leadingDot: Color? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingDot != null) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(leadingDot)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MetaText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun SectionSpacer(height: Dp = 12.dp) = Spacer(Modifier.height(height))

@Composable
fun RetryRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onRetry) { Text(stringResource(R.string.m3_load_more_failed)) }
    }
}

/**
 * Bar shown under the top bar while the device is offline.
 *
 * It is deliberately not a blocking error: whatever was browsed before is still readable (see the
 * offline HTTP cache), and the bar is there to explain why nothing new appears.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val online by OfflineStatus.online.collectAsStateWithLifecycle()
    if (online) return
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.m3_offline_banner), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) { Text(text) }
}

@Composable
fun CompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

// ----------------------------------------------------------------------------- formatting helpers

private val rtf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

/** "just now / 12 minutes ago / 3 hours ago / 2 days ago / 2023-05-01" (follows the system language) */
@Composable
fun relativeTime(date: Date?): String {
    if (date == null) return ""
    val diff = System.currentTimeMillis() - date.time
    if (diff < 0) return rtf.format(date)
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> stringResource(R.string.m3_just_now)
        minutes < 60 -> stringResource(R.string.m3_minutes_ago, minutes)
        hours < 24 -> stringResource(R.string.m3_hours_ago, hours)
        days < 30 -> stringResource(R.string.m3_days_ago, days)
        days < 365 -> stringResource(R.string.m3_months_ago, days / 30)
        else -> stringResource(R.string.m3_years_ago, days / 365)
    }
}

/** Same formatting for the rewritten data layer, which keeps [Instant]s instead of [Date]s. */
@Composable
fun relativeTime(at: Instant?): String {
    if (at == null) return ""
    return relativeTime(Date.from(at))
}

/** 1234 -> "1.2k" */
fun compactNumber(value: Long): String {
    val abs = abs(value)
    return when {
        abs < 1_000 -> value.toString()
        abs < 1_000_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
        else -> String.format(Locale.US, "%.1fm", value / 1_000_000.0)
    }
}

/** 1234567 -> "1.2 MB" (used by the offline-cache settings row). */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

/** Colour shown next to a repository's language, reusing the app's colours.json if loaded. */
@Composable
fun languageDot(color: Long?): Color =
    if (color != null && color != 0L) Color(color) else MaterialTheme.colorScheme.outline
