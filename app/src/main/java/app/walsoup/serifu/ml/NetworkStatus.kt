package app.walsoup.serifu.ml

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Whether a download is allowed to use the active network when the user asked
 * for unmetered Wi-Fi. Ethernet counts, metered hotspots do not, and a VPN is
 * judged by the network underneath it.
 */
object NetworkStatus {
    fun isUnmetered(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        return isUnmetered(cm)
    }

    fun isUnmetered(cm: ConnectivityManager): Boolean {
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        if (isAcceptable(caps)) return true
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false
        return underlyingCapabilities(cm, active, caps).any { isAcceptable(it) }
    }

    private fun isAcceptable(caps: NetworkCapabilities): Boolean {
        val lan = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return lan && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun underlyingCapabilities(
        cm: ConnectivityManager,
        active: Network,
        caps: NetworkCapabilities,
    ): List<NetworkCapabilities> {
        val underlying = underlyingNetworks(caps)
        if (!underlying.isNullOrEmpty()) {
            return underlying.mapNotNull { cm.getNetworkCapabilities(it) }
        }
        return cm.allNetworks.mapNotNull { network ->
            if (network == active) return@mapNotNull null
            val other = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (other.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                !other.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !other.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                return@mapNotNull null
            }
            other
        }
    }

    /**
     * Public SDK stubs omit this API 33 method (`@hide` on [NetworkCapabilities]).
     * Reflection keeps the call on devices that expose it; otherwise the caller
     * falls through to every non-VPN network.
     */
    private fun underlyingNetworks(caps: NetworkCapabilities): Array<Network>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return try {
            val method = NetworkCapabilities::class.java.getMethod("getUnderlyingNetworks")
            method.invoke(caps) as? Array<Network>
        } catch (_: ReflectiveOperationException) {
            null
        }
    }
}
