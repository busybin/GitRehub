package io.github.busybin.gitrehub.helper

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * "Am I online right now?" — the one place the app answers that question.
 *
 * The modern UI uses it both to decide whether to fall back to the HTTP cache
 * (see `OfflineRequestInterceptor`) and to show the offline banner. It is the only implementation.
 *
 * Before [init] runs (or when there is no ConnectivityManager at all) it reports *online*, which is
 * the safe default: requests go to the network first, and only fall back to the cache on failure.
 *
 * ## Why the callbacks do not simply ask `activeNetwork` (2026-10-17 bug)
 * The first version called `hasInternet(manager)` from every callback. That is wrong: `onAvailable`
 * can be delivered *before* the system has promoted the network to the default one, so
 * `activeNetwork` is still null/stale at that instant and the reading comes back "offline". No
 * further callback arrives (the network is already up), so the banner stayed on forever — exactly
 * the "locked the screen once, now it is stuck in offline mode even though there is a network"
 * report. The fix: read the state from the network the callback is *about*
 * ([NetworkCapabilities]), re-read from `activeNetwork` shortly after any callback to self-heal a
 * stale reading, and let the host activity [refresh] on resume.
 */
object OfflineStatus {

    private var manager: ConnectivityManager? = null

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recheck: Job? = null

    /** Registers a network callback; safe to call more than once. */
    fun init(context: Context) {
        if (manager != null) return
        val cm = context.applicationContext
            .getSystemService(ConnectivityManager::class.java) ?: return
        manager = cm
        refresh()
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    /**
     * Re-reads the state from the system. Cheap and thread-safe; call it whenever the app comes
     * back to the foreground — a suspended process can miss the `onAvailable` that ends an offline
     * spell, and this is the belt to the callback's braces.
     */
    fun refresh() {
        val cm = manager ?: return
        _online.value = hasInternet(cm)
    }

    /** Synchronous check, used on the OkHttp interceptor thread. */
    val isOnline: Boolean get() = manager?.let(::hasInternet) ?: true

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish(network)

        override fun onLost(network: Network) = publish(null)

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            _online.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            scheduleRecheck()
        }
    }

    /**
     * @param from the network the callback is about; null when it was lost (then the truth is
     *   whatever is the default network *now*).
     */
    private fun publish(from: Network?) {
        val cm = manager ?: return
        val caps = from?.let { cm.getNetworkCapabilities(it) }
        when {
            caps != null -> _online.value =
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            // A brand-new network with no capabilities yet: don't guess, re-read in a moment.
            from != null -> Unit
            else -> _online.value = hasInternet(cm)
        }
        scheduleRecheck()
    }

    /** `activeNetwork` lags the callbacks slightly; one delayed re-read makes the state converge. */
    private fun scheduleRecheck() {
        recheck?.cancel()
        recheck = scope.launch {
            delay(1200)
            refresh()
        }
    }

    private fun hasInternet(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * Whether browsing content may be kept on disk (and replayed while offline).
 *
 * Off by default is *not* what we want — a client that cannot work on a plane/train is exactly the
 * complaint this feature answers — so an absent value means enabled. Stored through [PrefHelper]
 * with the other app settings.
 */
object OfflinePrefs {

    private const val KEY_ENABLED = "m3_offline_cache"

    private val _enabled = MutableStateFlow(readEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val isEnabled: Boolean get() = _enabled.value

    fun setEnabled(value: Boolean) {
        PrefHelper.putAny(KEY_ENABLED, value)
        _enabled.value = value
    }

    /** Tolerates [PrefHelper] not being initialised yet (very early requests during startup). */
    private fun readEnabled(): Boolean = runCatching {
        !PrefHelper.isExist<Boolean>(KEY_ENABLED) || PrefHelper.getBoolean(KEY_ENABLED)
    }.getOrDefault(true)
}
