package io.github.busybin.gitrehub.provider.tasks.notification

import android.app.IntentService
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import android.net.Uri
import androidx.core.app.NotificationCompat
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.helper.AppHelper.cancelNotification
import io.github.busybin.gitrehub.helper.BundleConstant
import io.github.busybin.gitrehub.helper.InputHelper.getSafeIntId
import io.github.busybin.gitrehub.helper.PrefGetter.isMarkAsReadEnabled
import io.github.busybin.gitrehub.provider.scheme.SchemeParser.launchUri
import kotlinx.coroutines.runBlocking

/**
 * Created by Kosh on 11 Mar 2017, 12:13 AM
 */
class ReadNotificationService : IntentService("ReadNotificationService") {
    private var notification: NotificationCompat.Builder? = null
        get() {
            if (field == null) {
                field = NotificationCompat.Builder(this, "read-notification")
                    .setContentTitle(getString(R.string.marking_as_read))
                    .setSmallIcon(R.drawable.ic_sync)
                    .setProgress(0, 100, true)
            }
            return field
        }
    private var notificationManager: NotificationManager? = null
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    fun handleIntent(p0: Intent) {
        onHandleIntent(p0)
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null && intent.extras != null) {
            val bundle = intent.extras
            when (bundle!!.getInt(BundleConstant.EXTRA_TYPE)) {
                READ_SINGLE -> {
                    markSingleAsRead(bundle.getLong(BundleConstant.ID))
                }
                READ_ALL -> {
                    markMultiAsRead(bundle.getLongArray(BundleConstant.ID))
                }
                OPEN_NOTIFICATION -> {
                    openNotification(
                        bundle.getLong(BundleConstant.ID), bundle.getString(BundleConstant.EXTRA),
                        bundle.getBoolean(BundleConstant.YES_NO_EXTRA)
                    )
                }
                UN_SUBSCRIBE -> {
                    unSubscribeFromThread(bundle.getLong(BundleConstant.ID))
                }
            }
        }
    }

    private fun unSubscribeFromThread(id: Long) {
        notify(id, notification!!.build())
        runCatching { runBlocking { GitHubApi.unsubscribeThread(id.toString()) } }
        markSingleAsRead(id)
    }

    private fun openNotification(id: Long, url: String?, readOnly: Boolean) {
        if (id > 0 && url != null) {
            cancelNotification(this, getSafeIntId(id))
            if (readOnly) {
                markSingleAsRead(id)
            } else if (!isMarkAsReadEnabled) {
                markSingleAsRead(id)
            }
            if (!readOnly) {
                launchUri(applicationContext, Uri.parse(url),
                    showRepoBtn = true,
                    newDocument = true
                )
            }
        }
    }

    private fun markMultiAsRead(ids: LongArray?) {
        if (ids != null && ids.isNotEmpty()) {
            ids.forEach { id: Long -> markSingleAsRead(id) }
        }
    }

    private fun markSingleAsRead(id: Long) {
        notify(id, notification!!.build())
        runCatching { runBlocking { GitHubApi.markThreadRead(id.toString()) } }
        cancel(id)
    }

    private fun notify(id: Long, notification: Notification) {
        notificationManager!!.notify(getSafeIntId(id), notification)
    }

    private fun cancel(id: Long) {
        notificationManager!!.cancel(getSafeIntId(id))
    }

    companion object {
        const val READ_SINGLE = 1
        const val READ_ALL = 2
        const val OPEN_NOTIFICATION = 3
        const val UN_SUBSCRIBE = 4
        fun start(context: Context, id: Long) {
            val intent = Intent(context.applicationContext, ReadNotificationService::class.java)
            intent.putExtras(
                bundleOf(BundleConstant.EXTRA_TYPE to READ_SINGLE, BundleConstant.ID to id)
            )
            context.startService(intent)
        }

        @JvmOverloads
        fun start(context: Context, id: Long, url: String, onlyRead: Boolean = false): Intent {
            val intent = Intent(context.applicationContext, ReadNotificationService::class.java)
            intent.putExtras(
                bundleOf(
                    BundleConstant.EXTRA_TYPE to OPEN_NOTIFICATION,
                    BundleConstant.EXTRA to url,
                    BundleConstant.ID to id,
                    BundleConstant.YES_NO_EXTRA to onlyRead,
                )
            )
            return intent
        }

        fun unSubscribe(context: Context, id: Long) {
            val intent = Intent(context.applicationContext, ReadNotificationService::class.java)
            intent.putExtras(
                bundleOf(BundleConstant.EXTRA_TYPE to UN_SUBSCRIBE, BundleConstant.ID to id)
            )
            context.startService(intent)
        }

        fun start(context: Context, ids: LongArray) {
            val intent = Intent(context.applicationContext, ReadNotificationService::class.java)
            intent.putExtras(
                bundleOf(BundleConstant.EXTRA_TYPE to READ_ALL, BundleConstant.ID to ids)
            )
            context.startService(intent)
        }
    }
}