package io.github.busybin.gitrehub.provider.tasks.notification

import android.app.IntentService
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.core.auth.Session
import io.github.busybin.gitrehub.core.auth.TokenStore
import io.github.busybin.gitrehub.core.net.GitHubApi
import io.github.busybin.gitrehub.helper.AppHelper.cancelNotification
import io.github.busybin.gitrehub.helper.BundleConstant
import io.github.busybin.gitrehub.helper.InputHelper.getSafeIntId
import io.github.busybin.gitrehub.helper.PrefGetter.isMarkAsReadEnabled
import io.github.busybin.gitrehub.provider.scheme.SchemeParser.launchUri
import kotlinx.coroutines.runBlocking

/**
 * Created by Kosh on 11 Mar 2017, 12:13 AM
 *
 * Acts on the notification shade entries. Every intent carries [BundleConstant.ACCOUNT] because the
 * pollers watch all stored accounts: mark-read / Done must use that account's token, and opening a
 * thread of another account has to switch to it first (the UI restarts as that account).
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
            val account = bundle!!.getString(BundleConstant.ACCOUNT)
            when (bundle.getInt(BundleConstant.EXTRA_TYPE)) {
                READ_SINGLE -> {
                    markSingleAsRead(bundle.getLong(BundleConstant.ID), account)
                }
                READ_ALL -> {
                    markMultiAsRead(bundle.getLongArray(BundleConstant.ID), account)
                }
                OPEN_NOTIFICATION -> {
                    openNotification(
                        bundle.getLong(BundleConstant.ID), bundle.getString(BundleConstant.EXTRA),
                        bundle.getBoolean(BundleConstant.YES_NO_EXTRA), account,
                    )
                }
                UN_SUBSCRIBE -> {
                    unSubscribeFromThread(bundle.getLong(BundleConstant.ID), account)
                }
            }
        }
    }

    /** Token of the account the thread belongs to; null (older notifications) means the current one. */
    private fun tokenFor(login: String?): String? =
        if (login.isNullOrBlank()) null else TokenStore.tokenFor(login)

    /** The account was removed between posting and tapping: never act as the current account instead. */
    private fun accountGone(login: String?): Boolean =
        !login.isNullOrBlank() && TokenStore.tokenFor(login) == null

    private fun unSubscribeFromThread(id: Long, login: String?) {
        notify(id, notification!!.build())
        if (!accountGone(login)) {
            runCatching {
                runBlocking { GitHubApi.unsubscribeThread(id.toString(), tokenFor(login)) }
            }
        }
        markSingleAsRead(id, login)
    }

    private fun openNotification(id: Long, url: String?, readOnly: Boolean, login: String?) {
        if (id > 0 && url != null) {
            cancelNotification(this, getSafeIntId(id))
            if (readOnly) {
                markSingleAsRead(id, login)
            } else if (!isMarkAsReadEnabled) {
                markSingleAsRead(id, login)
            }
            if (!readOnly) {
                // A thread of another account only makes sense under that account (private
                // repositories, shared read state). Switching restarts the UI on the new account;
                // the route then opens on top of it.
                val currentLogin = runBlocking { Session.currentAccount()?.login }
                val target = login?.takeIf { !it.equals(currentLogin, ignoreCase = true) }
                if (target != null) {
                    val ok = runBlocking { Session.switchTo(target) }
                    if (!ok) {
                        Toast.makeText(this, R.string.m3_accounts_switch_failed, Toast.LENGTH_SHORT)
                            .show()
                        return
                    }
                    Toast.makeText(
                        this,
                        getString(R.string.m3_accounts_switched, target),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                launchUri(
                    applicationContext,
                    Uri.parse(url),
                    showRepoBtn = true,
                    newDocument = true,
                    freshTask = target != null,
                )
            }
        }
    }

    private fun markMultiAsRead(ids: LongArray?, login: String?) {
        if (ids != null && ids.isNotEmpty()) {
            ids.forEach { id: Long -> markSingleAsRead(id, login) }
        }
    }

    private fun markSingleAsRead(id: Long, login: String?) {
        notify(id, notification!!.build())
        if (!accountGone(login)) {
            runCatching { runBlocking { GitHubApi.markThreadRead(id.toString(), tokenFor(login)) } }
        }
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

        /**
         * Distinct action per pending-intent kind. `PendingIntent` identity is (request code,
         * action, data, type, component) but **not** extras, so without these the content intent and
         * the "mark read" action of the same thread share one record and overwrite each other.
         */
        const val ACTION_OPEN = "io.github.busybin.gitrehub.OPEN_NOTIFICATION"
        const val ACTION_READ = "io.github.busybin.gitrehub.READ_NOTIFICATION"
        const val ACTION_DONE = "io.github.busybin.gitrehub.DONE_NOTIFICATION"
        fun start(context: Context, id: Long) {
            val intent = Intent(context.applicationContext, ReadNotificationService::class.java)
            intent.putExtras(
                bundleOf(BundleConstant.EXTRA_TYPE to READ_SINGLE, BundleConstant.ID to id)
            )
            context.startService(intent)
        }

        @JvmOverloads
        fun start(
            context: Context,
            id: Long,
            url: String,
            onlyRead: Boolean = false,
            login: String? = null,
        ): Intent {
            val intent = Intent(context.applicationContext, ReadNotificationService::class.java)
            intent.action = ACTION_OPEN
            intent.putExtras(
                bundleOf(
                    BundleConstant.EXTRA_TYPE to OPEN_NOTIFICATION,
                    BundleConstant.EXTRA to url,
                    BundleConstant.ID to id,
                    BundleConstant.YES_NO_EXTRA to onlyRead,
                    BundleConstant.ACCOUNT to login,
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
