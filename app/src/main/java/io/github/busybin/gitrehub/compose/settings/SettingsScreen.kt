package io.github.busybin.gitrehub.compose.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.busybin.gitrehub.BuildConfig
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LanguagePrefs
import io.github.busybin.gitrehub.compose.common.MarkdownText
import io.github.busybin.gitrehub.compose.common.Density
import io.github.busybin.gitrehub.compose.common.ReadState
import io.github.busybin.gitrehub.compose.common.SwipeAction
import io.github.busybin.gitrehub.compose.common.SwipePrefs
import io.github.busybin.gitrehub.compose.common.UiText
import io.github.busybin.gitrehub.compose.common.UpdateInstaller
import io.github.busybin.gitrehub.compose.common.render
import io.github.busybin.gitrehub.compose.common.restartModernUi
import io.github.busybin.gitrehub.compose.common.formatBytes
import io.github.busybin.gitrehub.compose.data.UpdateChecker
import io.github.busybin.gitrehub.compose.data.UpdatePrefs
import io.github.busybin.gitrehub.compose.feed.FeedPrefs
import io.github.busybin.gitrehub.compose.feed.FeedReadFilter
import io.github.busybin.gitrehub.compose.feed.joinKeywords
import io.github.busybin.gitrehub.compose.notifications.NotificationPrefs
import io.github.busybin.gitrehub.compose.theme.ThemeId
import io.github.busybin.gitrehub.compose.theme.ThemePrefs
import io.github.busybin.gitrehub.compose.theme.ThemeState
import io.github.busybin.gitrehub.compose.theme.scheme
import io.github.busybin.gitrehub.compose.theme.spec
import io.github.busybin.gitrehub.helper.BrowserLauncher
import io.github.busybin.gitrehub.helper.LinkHandling
import io.github.busybin.gitrehub.helper.OfflinePrefs
import io.github.busybin.gitrehub.provider.rest.HttpCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val themeId by ThemeState.theme.collectAsStateWithLifecycle()
    val fontScale by ThemeState.fontScale.collectAsStateWithLifecycle()
    val density by ThemeState.density.collectAsStateWithLifecycle()
    val language by LanguagePrefs.tag.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Sliders only move a local draft while dragging: applying the value live rebuilds the whole
    // app (font scale swaps LocalDensity, density re-lays out every screen), which interrupts the
    // gesture after a frame or two. The setting is committed on release.
    var fontDraft by remember(fontScale) { mutableFloatStateOf(fontScale) }
    var densityDraft by remember(density) { mutableFloatStateOf(density) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionTitle(stringResource(R.string.m3_settings_appearance))
        // In-app language: the modern UI ships eight locales (see LanguagePrefs), so the options are
        // "follow system" plus those eight; switching rebuilds the UI so attachBaseContext takes effect.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.m3_language), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguagePrefs.options.forEach { option ->
                        FilterChip(
                            selected = language == option,
                            onClick = {
                                if (language != option) {
                                    LanguagePrefs.set(option)
                                    restartModernUi(context)
                                }
                            },
                            label = { Text(stringResource(LanguagePrefs.labelRes(option))) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.m3_font_size),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(fontDraft * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = fontDraft,
                    onValueChange = { fontDraft = it },
                    onValueChangeFinished = { ThemeState.setFontScale(fontDraft) },
                    valueRange = ThemePrefs.fontScaleMin..ThemePrefs.fontScaleMax,
                    modifier = Modifier.testTag("font_scale_slider"),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = stringResource(R.string.m3_color_theme),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
                val dark = true
                ThemeId.entries
                    .filter { it != ThemeId.WALLPAPER || ThemePrefs.supportsDynamicColor }
                    .forEach { id ->
                        ThemeRow(
                            id = id,
                            dark = dark,
                            selected = themeId == id,
                            onSelect = { ThemeState.setTheme(id) },
                        )
                    }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.m3_open_links), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.m3_open_links_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val context = LocalContext.current
                TextButton(onClick = { LinkHandling.openSettings(context) }) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.m3_open_system_settings))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.m3_interface_density),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(densityDraft * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.m3_interface_density_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = densityDraft,
                    onValueChange = { densityDraft = it },
                    onValueChangeFinished = { ThemeState.setDensity(densityDraft) },
                    valueRange = Density.MIN..Density.MAX,
                    modifier = Modifier.testTag("density_slider"),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.m3_section_notifications))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                NotificationSettings()
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.m3_section_gestures))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                SwipeSettings()
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.m3_section_feed_filtering))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                FeedFilterSettings()
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.m3_section_offline_cache))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                OfflineCacheSettings()
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.m3_section_about))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = "GitRehub",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    // Version numbers are plain integers (release tags carry no `V` prefix) and match VERSION_NAME.
                    text = stringResource(R.string.m3_version_short, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                UpdateRow()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * "Check for updates": automatic (checked once when this screen opens, no manual tap) and shows the
 * status; when a newer version exists a "download and update" button hands the APK to the system
 * installer (see [UpdateInstaller]). Shares [UpdateChecker] and [UpdatePrefs] with [UpdatePromptHost].
 */
@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val auto by UpdatePrefs.auto.collectAsStateWithLifecycle()

    fun checkNow() {
        scope.launch {
            result = runCatching { UpdateChecker.check(BuildConfig.VERSION_CODE) }.getOrNull()
                ?: UpdateChecker.Result.Failed(UiText.res(R.string.m3_update_check_failed))
        }
    }

    // Automatic checks are off by default (see UpdatePrefs.auto): requests sent without asking count
    // as tracking under F-Droid's definition, so the user must opt in; the "check" action above always
    // works because the user tapped it.
    LaunchedEffect(auto) { if (auto) checkNow() }

    val available = result as? UpdateChecker.Result.Available

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.m3_update_title), style = MaterialTheme.typography.bodyLarge)
                val message = when (val r = result) {
                    null -> stringResource(R.string.m3_version_short, BuildConfig.VERSION_NAME)
                    is UpdateChecker.Result.UpToDate -> stringResource(R.string.m3_update_uptodate)
                    is UpdateChecker.Result.Available -> stringResource(R.string.m3_update_available, r.tag)
                    is UpdateChecker.Result.Failed -> r.message.render()
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (result) {
                        is UpdateChecker.Result.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            TextButton(onClick = { checkNow() }) {
                Text(stringResource(R.string.m3_update_check))
            }
            if (available != null) {
                TextButton(
                    enabled = !downloading,
                    onClick = {
                        val apk = available.apkUrl
                        if (apk.isNullOrBlank()) {
                            // No APK asset (or not reachable): fall back to the release page.
                            BrowserLauncher.open(context, available.url)
                            return@TextButton
                        }
                        if (!UpdateInstaller.canInstall(context)) {
                            Toast.makeText(context, R.string.m3_update_install_permission, Toast.LENGTH_LONG).show()
                            UpdateInstaller.openInstallSettings(context)
                            return@TextButton
                        }
                        downloading = true
                        scope.launch {
                            val file = runCatching { UpdateInstaller.download(context, apk, available.tag) }.getOrNull()
                            downloading = false
                            if (file == null) {
                                Toast.makeText(context, R.string.m3_update_download_failed, Toast.LENGTH_LONG).show()
                            } else if (UpdateInstaller.install(context, file)) {
                                Toast.makeText(context, R.string.m3_update_install_ready, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, R.string.m3_update_download_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                ) {
                    if (downloading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.m3_update_download_install))
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { UpdatePrefs.setAuto(!auto) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.m3_update_auto_check),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.m3_update_auto_check_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = auto,
                onCheckedChange = {
                    UpdatePrefs.setAuto(it)
                    if (it) checkNow()
                },
            )
        }

        if (available != null && !available.notes.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.m3_update_changelog),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            // The changelog is the release body (Markdown).
            MarkdownText(markdown = available.notes)
        }
    }
}

/**
 * "New message notifications": toggle + interval. Enabling it (Android 13+) requests the
 * `POST_NOTIFICATIONS` runtime permission first: without it the scheduled job still runs but nothing is
 * shown, so the permission must be obtained at the moment the toggle is turned on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationSettings() {
    val context = LocalContext.current
    val enabled by NotificationPrefs.enabled.collectAsStateWithLifecycle()
    val interval by NotificationPrefs.intervalMinutes.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> NotificationPrefs.setEnabled(context, granted) }

    fun needsNotifPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    fun requestEnable() {
        if (needsNotifPermission()) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationPrefs.setEnabled(context, true)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.m3_notif_new_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.m3_notif_new_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { if (it) requestEnable() else NotificationPrefs.setEnabled(context, false) },
        )
    }

    Spacer(Modifier.height(10.dp))
    val foreground by NotificationPrefs.foreground.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.m3_notif_fg_switch), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.m3_notif_fg_switch_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = foreground,
            onCheckedChange = { on ->
                // The foreground service also needs the notification permission; if "new message
                // notifications" is on, the permission is already granted.
                if (on && needsNotifPermission()) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    NotificationPrefs.setForegroundEnabled(context, on)
                }
            },
        )
    }

    if (enabled) {
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.m3_notif_interval), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        // FlowRow so the seven chips wrap; a plain Row clipped the extras.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NotificationPrefs.intervals.forEach { minutes ->
                FilterChip(
                    selected = interval == minutes,
                    onClick = { NotificationPrefs.setIntervalMinutes(context, minutes) },
                    label = {
                        val res = NotificationPrefs.labelRes(minutes)
                        Text(
                            if (res == R.string.m3_notif_every_n_min) stringResource(res, minutes)
                            else stringResource(res)
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.m3_notif_interval_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwipeSettings() {
    val start by SwipePrefs.start.collectAsStateWithLifecycle()
    val end by SwipePrefs.end.collectAsStateWithLifecycle()

    Text(
        text = stringResource(R.string.m3_swipe_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    SwipeDirectionRow(
        title = stringResource(R.string.m3_swipe_right),
        selected = start,
        onSelect = { SwipePrefs.setStart(it) },
    )
    Spacer(Modifier.height(12.dp))
    SwipeDirectionRow(
        title = stringResource(R.string.m3_swipe_left),
        selected = end,
        onSelect = { SwipePrefs.setEnd(it) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwipeDirectionRow(
    title: String,
    selected: SwipeAction,
    onSelect: (SwipeAction) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SwipeAction.entries.forEach { action ->
                FilterChip(
                    selected = selected == action,
                    onClick = { onSelect(action) },
                    label = { Text(stringResource(action.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * Feed filtering: blocked keywords (feed rows whose user/repo matches are hidden) and the optional
 * "hide what I already read" switch.
 */
@Composable
private fun FeedFilterSettings() {
    val filter by FeedPrefs.filter.collectAsStateWithLifecycle()
    val readIds by ReadState.readIds.collectAsStateWithLifecycle()
    val readKeys by ReadState.readKeys.collectAsStateWithLifecycle()
    val readCount = readIds.size + readKeys.size

    Text(stringResource(R.string.m3_filter_keywords), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.m3_muted_keywords_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = joinKeywords(filter.keywords),
        onValueChange = { FeedPrefs.setBlocked(it) },
        placeholder = { Text(stringResource(R.string.m3_filter_keywords_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.m3_read_activity), style = MaterialTheme.typography.bodyLarge)
    Text(
        text = stringResource(R.string.m3_read_activity_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FeedReadFilter.entries.forEach { entry ->
            FilterChip(
                selected = filter.readFilter == entry,
                onClick = { FeedPrefs.setReadFilter(entry) },
                label = { Text(stringResource(entry.labelRes)) },
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.m3_more_filters),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (filter.activeCount > 0) {
                stringResource(R.string.m3_filters_active, filter.activeCount)
            } else {
                stringResource(R.string.m3_no_filters)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { FeedPrefs.clearFilter() },
            enabled = !filter.isDefault,
        ) { Text(stringResource(R.string.m3_filter_clear)) }
    }

    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.m3_read_recorded, readCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { ReadState.clear() },
            enabled = readCount > 0,
        ) { Text(stringResource(R.string.m3_clear_record)) }
    }
}

/**
 * Offline cache: the switch that decides whether browsing data may be kept on disk, plus the size
 * of what is there now and a way to throw it away.
 */
@Composable
private fun OfflineCacheSettings() {
    val enabled by OfflinePrefs.enabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf<Long?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    // Reading the cache directory size touches the file system, so keep it off the UI thread.
    LaunchedEffect(reload, enabled) {
        size = withContext(Dispatchers.IO) { HttpCache.sizeBytes() }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.m3_use_cache_offline), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.m3_use_cache_offline_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = { OfflinePrefs.setEnabled(it) })
    }

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = size?.let { stringResource(R.string.m3_cache_used, formatBytes(it)) }
                ?: stringResource(R.string.m3_cache_counting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { HttpCache.clear() }
                    reload++
                }
            },
            enabled = (size ?: 0L) > 0L,
        ) { Text(stringResource(R.string.m3_clear_cache)) }
    }
}

/**
 * One theme row: a real palette preview on the left (background + primary + keyword + string) and the
 * name plus a one-line description on the right. The preview uses the theme's own computed colours.
 */
@Composable
private fun ThemeRow(
    id: ThemeId,
    dark: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeSwatch(id = id, dark = dark)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(id.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = stringResource(id.blurbRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.m3_current_theme),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ThemeSwatch(id: ThemeId, dark: Boolean) {
    val context = LocalContext.current
    val fallback = MaterialTheme.colorScheme
    // Wallpaper-following has no base tones, so ask the system; the preview must be the real thing.
    val spec = id.spec
    val scheme = when {
        spec != null -> if (dark) spec.dark.scheme(true) else spec.light.scheme(false)
        ThemePrefs.supportsDynamicColor ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> fallback
    }
    val code = spec?.let { if (dark) it.darkCode else it.lightCode }
    val shape = RoundedCornerShape(9.dp)

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            SwatchBar(scheme.primary, 0.9f)
            SwatchBar(code?.keyword ?: scheme.secondary, 1f)
            SwatchBar(code?.string ?: scheme.onSurfaceVariant, 0.65f)
        }
    }
}

@Composable
private fun SwatchBar(color: Color, widthFraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

