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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    // A static placeholder: a looping pulse on a screen that is not live reads as decoration and
    // costs a frame every 16 ms while the wait is already the slowest part.
    val alpha = 0.5f
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
            // A plain AsyncImage, not SubcomposeAsyncImage: the latter adds a subcomposition per
            // avatar, and the feed can hold dozens. The circle background is the placeholder.
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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

/**
 * A label. Two readings:
 *
 * - quiet (default): no fill, no pill shape, just an optional 6dp dot and muted text, so language,
 *   fork, "read" and similar meta stop competing with the row's content;
 * - filled: pass [container]/[content] and it becomes a small 6dp-radius chip, used only where the
 *   colour carries meaning (GitHub labels, diff counts, review states).
 */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = Color.Unspecified,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingDot: Color? = null,
) {
    val filled = container != Color.Unspecified
    Row(
        modifier = modifier
            .then(if (filled) Modifier.clip(RoundedCornerShape(6.dp)).background(container) else Modifier)
            .padding(horizontal = if (filled) 6.dp else 0.dp, vertical = if (filled) 2.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingDot != null) {
            Box(
                Modifier
                    .size(6.dp)
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

/**
 * Meta line: 12sp, muted, tabular figures. [mono] switches to the monospace family for identifiers
 * (owner/repo, file paths, commit SHAs), which is this app's one signature detail.
 */
@Composable
fun MetaText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    mono: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        fontFamily = if (mono) FontFamily.Monospace else null,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Repository, path or SHA: monospace, readable brightness, never accent-coloured. */
@Composable
fun Identifier(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    emphasized: Boolean = false,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = style,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Section heading inside a scrolling page. Sentence case, muted, no tracking. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

/** One hairline between list rows; the only separator the design needs. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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

/** Future dates (clock skew in an event's timestamp) use a plain date instead of "in -3 minutes". */
private fun formatDate(date: Date, locale: Locale): String =
    SimpleDateFormat("yyyy-MM-dd", locale).format(date)

/** "just now / 12 minutes ago / 3 hours ago / 2 days ago / 2023-05-01" (follows the app language) */
@Composable
fun relativeTime(date: Date?): String {
    if (date == null) return ""
    val appLocale = LocalConfiguration.current.locales[0]
    val diff = System.currentTimeMillis() - date.time
    if (diff < 0) return formatDate(date, appLocale)
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> stringResource(R.string.m3_just_now)
        minutes < 60 -> pluralStringResource(R.plurals.m3_minutes_ago, minutes.toInt(), minutes.toInt())
        hours < 24 -> pluralStringResource(R.plurals.m3_hours_ago, hours.toInt(), hours.toInt())
        days < 30 -> pluralStringResource(R.plurals.m3_days_ago, days.toInt(), days.toInt())
        days < 365 -> pluralStringResource(R.plurals.m3_months_ago, (days / 30).toInt(), (days / 30).toInt())
        else -> pluralStringResource(R.plurals.m3_years_ago, (days / 365).toInt(), (days / 365).toInt())
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

/**
 * GitHub label colour (`rrggbb`, sometimes `#rrggbb`) as a Compose colour.
 *
 * Labels are the only place where an arbitrary colour is meaningful: it is the label's identity, so
 * it becomes a 6dp dot next to a quiet name instead of a filled chip.
 */
/**
 * Semantic "open" colour. GitHub's dark green, one step toned down so it sits inside the palette
 * instead of glowing; only issue/PR state uses it, nothing else.
 */
@Composable
fun openGreen(): Color = Color(0xFF3FB950)

fun labelColor(hex: String?): Color? {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    if (clean.length != 6) return null
    return runCatching { Color(android.graphics.Color.parseColor("#$clean")) }.getOrNull()
}

