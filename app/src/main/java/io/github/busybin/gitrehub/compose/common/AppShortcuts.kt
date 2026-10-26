package io.github.busybin.gitrehub.compose.common

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.MainComposeActivity
import io.github.busybin.gitrehub.compose.Routes
import io.github.busybin.gitrehub.provider.scheme.SchemeParser

/**
 * Long-press shortcuts on the launcher icon. Dynamic rather than `res/xml/shortcuts.xml` because
 * the intents have to name this build's own activity (the debug build has a different id).
 */
object AppShortcuts {

    private data class Entry(val id: String, val label: Int, val route: String)

    private val entries = listOf(
        Entry("notifications", R.string.m3_title_notifications, Routes.NOTIFICATIONS),
        Entry("my-work", R.string.m3_my_work, Routes.MY_WORK),
        Entry("search", R.string.m3_drawer_search, Routes.SEARCH),
        Entry("trending", R.string.m3_title_trending, Routes.TRENDING),
    )

    fun publish(context: Context) {
        val shortcuts = entries.map { entry ->
            ShortcutInfoCompat.Builder(context, entry.id)
                .setShortLabel(context.getString(entry.label))
                .setLongLabel(context.getString(entry.label))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(context, MainComposeActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(SchemeParser.EXTRA_NAVIGATE, entry.route),
                )
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }
}
