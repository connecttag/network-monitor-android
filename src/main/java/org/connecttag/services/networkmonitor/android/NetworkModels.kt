package org.connecttag.services.networkmonitor.android

import kotlinx.coroutines.flow.StateFlow

public interface NetworkMonitor : AutoCloseable {
    public val status: StateFlow<NetworkStatus>

    public fun start()

    public fun stop()

    public fun refresh()
}

public data class NetworkStatus(
    val availability: NetworkAvailability = NetworkAvailability.Unknown,
    val internet: InternetStatus = InternetStatus.Unknown,
    val transports: Set<NetworkTransport> = emptySet(),
    val isMetered: Boolean? = null,
    val isRoaming: Boolean? = null,
    val downstreamKbps: Int? = null,
    val upstreamKbps: Int? = null,
    val activeNetworkId: String? = null,
    val lastChangedAtMillis: Long = 0L,
) {
    public val isConnected: Boolean
        get() = availability == NetworkAvailability.Available

    public val hasValidatedInternet: Boolean
        get() = internet == InternetStatus.Validated

    public companion object {
        public val Unknown: NetworkStatus = NetworkStatus()
        public fun unavailable(nowMillis: Long): NetworkStatus = NetworkStatus(
            availability = NetworkAvailability.Unavailable,
            internet = InternetStatus.Unavailable,
            lastChangedAtMillis = nowMillis,
        )
    }
}

public enum class NetworkAvailability {
    Unknown,
    Available,
    Unavailable,
    Losing,
    Lost,
}

public enum class InternetStatus {
    Unknown,
    Unavailable,
    CaptivePortal,
    HasCapability,
    Validated,
}

public enum class NetworkTransport {
    Wifi,
    Cellular,
    Ethernet,
    Bluetooth,
    Vpn,
    Usb,
    Lowpan,
    Thread,
    Satellite,
    Other,
}

