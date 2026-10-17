@file:Suppress("unused")

package org.connecttag.services.networkmonitor.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class AndroidNetworkMonitor(
    context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : NetworkMonitor, Closeable {

    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService<ConnectivityManager>()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val started = AtomicBoolean(false)

    private val _status = MutableStateFlow(readCurrentStatus())
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateFrom(network, NetworkAvailability.Available)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _status.value = networkCapabilities.toNetworkStatus(
                network = network,
                availability = NetworkAvailability.Available,
                nowMillis = clock(),
                isMetered = connectivityManager?.isActiveNetworkMetered,
            )
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            updateFrom(network, NetworkAvailability.Losing)
        }

        override fun onLost(network: Network) {
            _status.value = NetworkStatus.unavailable(clock()).copy(availability = NetworkAvailability.Lost)
        }

        override fun onUnavailable() {
            _status.value = NetworkStatus.unavailable(clock())
        }
    }

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        val manager = connectivityManager ?: run {
            _status.value = NetworkStatus.unavailable(clock())
            return
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                manager.registerNetworkCallback(request, callback)
            }
            refresh()
        }.onFailure {
            started.set(false)
            _status.value = NetworkStatus.unavailable(clock())
        }
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
    }

    override fun refresh() {
        scope.launch {
            _status.value = readCurrentStatus()
        }
    }

    override fun close() {
        stop()
    }

    private fun updateFrom(network: Network, availability: NetworkAvailability) {
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        _status.value = capabilities?.toNetworkStatus(
            network = network,
            availability = availability,
            nowMillis = clock(),
            isMetered = connectivityManager?.isActiveNetworkMetered,
        ) ?: NetworkStatus.unavailable(clock())
    }

    private fun readCurrentStatus(): NetworkStatus {
        val manager = connectivityManager ?: return NetworkStatus.unavailable(clock())
        val network = manager.activeNetwork ?: return NetworkStatus.unavailable(clock())
        val capabilities = manager.getNetworkCapabilities(network) ?: return NetworkStatus.unavailable(clock())
        return capabilities.toNetworkStatus(
            network = network,
            availability = NetworkAvailability.Available,
            nowMillis = clock(),
            isMetered = manager.isActiveNetworkMetered,
        )
    }
}

private fun NetworkCapabilities.toNetworkStatus(
    network: Network,
    availability: NetworkAvailability,
    nowMillis: Long,
    isMetered: Boolean?,
): NetworkStatus {
    val hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    val captivePortal = hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)

    return NetworkStatus(
        availability = availability,
        internet = when {
            isValidated -> InternetStatus.Validated
            captivePortal -> InternetStatus.CaptivePortal
            hasInternet -> InternetStatus.HasCapability
            else -> InternetStatus.Unavailable
        },
        transports = transports(),
        isMetered = isMetered,
        isRoaming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        } else {
            null
        },
        downstreamKbps = linkDownstreamBandwidthKbps.takeIf { it > 0 },
        upstreamKbps = linkUpstreamBandwidthKbps.takeIf { it > 0 },
        activeNetworkId = network.toString(),
        lastChangedAtMillis = nowMillis,
    )
}

private fun NetworkCapabilities.transports(): Set<NetworkTransport> {
    val result = linkedSetOf<NetworkTransport>()
    if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) result += NetworkTransport.Wifi
    if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) result += NetworkTransport.Cellular
    if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) result += NetworkTransport.Ethernet
    if (hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) result += NetworkTransport.Bluetooth
    if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) result += NetworkTransport.Vpn
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) result += NetworkTransport.Other
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) result += NetworkTransport.Lowpan
    if (hasOptionalTransport("TRANSPORT_USB")) result += NetworkTransport.Usb
    if (hasOptionalTransport("TRANSPORT_THREAD")) result += NetworkTransport.Thread
    if (hasOptionalTransport("TRANSPORT_SATELLITE")) result += NetworkTransport.Satellite
    return result.ifEmpty { setOf(NetworkTransport.Other) }
}

private fun NetworkCapabilities.hasOptionalTransport(fieldName: String): Boolean {
    return runCatching {
        val transport = NetworkCapabilities::class.java.getField(fieldName).getInt(null)
        hasTransport(transport)
    }.getOrDefault(false)
}


