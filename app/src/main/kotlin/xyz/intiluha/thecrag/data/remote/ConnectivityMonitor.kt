package xyz.intiluha.thecrag.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the device currently has a usable internet connection: the default
 * network reports INTERNET and VALIDATED (a captive portal or a dead wifi is
 * "offline"). One instance per process, registered on creation; the UI shows the
 * Offline bar and dims rows that are not on the device while [online] is false.
 */
class ConnectivityMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(isOnlineNow())
    val online: StateFlow<Boolean> = _online

    /** The default network as last reported by the callback; `onLost` of another network is ignored. */
    private var current: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            current = network
            // Android 8+ follows onAvailable with onCapabilitiesChanged right away; until then be optimistic.
            _online.value = true
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (network == current) _online.value = usable(caps)
        }

        override fun onLost(network: Network) {
            if (network == current) {
                current = null
                _online.value = false
            }
        }
    }

    init {
        // Throws on some devices when too many callbacks are registered; the initial check still stands.
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
    }

    private fun isOnlineNow(): Boolean {
        val manager = cm ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return usable(caps)
    }

    private fun usable(caps: NetworkCapabilities) =
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
