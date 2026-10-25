package io.github.busybin.gitrehub.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.MainComposeActivity
import io.github.busybin.gitrehub.helper.PrefHelper

/** Home-screen unread badge: unread notifications plus the My Work count. */
class UnreadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
    }

    companion object {
        private const val KEY_UNREAD = "m3_widget_unread"
        private const val KEY_MYWORK = "m3_widget_mywork"

        /** Counts changed: store them (a reboot renders the last value) and repaint every instance. */
        fun publish(context: Context, unread: Int, myWork: Int) {
            if (PrefHelper.getString(KEY_UNREAD) == unread.toString() &&
                PrefHelper.getString(KEY_MYWORK) == myWork.toString()
            ) return
            PrefHelper.putAny(KEY_UNREAD, unread.toString())
            PrefHelper.putAny(KEY_MYWORK, myWork.toString())
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, UnreadWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context): RemoteViews {
            val unread = PrefHelper.getString(KEY_UNREAD)?.toIntOrNull() ?: 0
            val myWork = PrefHelper.getString(KEY_MYWORK)?.toIntOrNull() ?: 0
            val views = RemoteViews(context.packageName, R.layout.widget_unread)
            views.setTextViewText(R.id.widget_count, if (unread > 99) "99+" else unread.toString())
            views.setTextViewText(
                R.id.widget_mywork,
                context.getString(R.string.m3_widget_mywork, myWork),
            )
            val intent = Intent(context, MainComposeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
