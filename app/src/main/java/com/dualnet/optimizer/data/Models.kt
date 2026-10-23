package com.dualnet.optimizer.data

import android.net.Network

enum class ConnectionMode(val title: String, val description: String) {
    DUAL("Dual Network Mode", "Use Wi-Fi and Mobile Data together for faster and more stable connection."),
    WIFI("Wi-Fi Only", "Use only Wi-Fi connection."),
    MOBILE("Mobile Data Only", "Use only mobile data connection."),
    SMART("Smart Mode", "Automatically use the best connection based on network conditions.")
}

data class Preferences(
    val mode: ConnectionMode = ConnectionMode.SMART,
    val autoStart: Boolean = false,
    val showSpeed: Boolean = false,
    val notifications: Boolean = true,
    val connectionLock: Boolean = false,
    val serverHost: String = "",
    val serverPort: Int = 4433,
    val token: String = "",
    val dns: String = "1.1.1.1",
    val routedApps: Set<String> = emptySet(),
    val mtu: Int = 1400,
    val probeInterval: Int = 30
) { val gatewayConfigured: Boolean get() = serverHost.isNotBlank() && token.isNotBlank() }

data class NetworkInfo(
    val network: Network? = null,
    val available: Boolean = false,
    val validated: Boolean = false,
    val name: String = "Unavailable",
    val ip: String = "—",
    val gateway: String = "—",
    val dns: String = "—",
    val interfaceName: String? = null,
    val signalDbm: Int? = null,
    val technology: String = "—",
    val carrier: String = "—",
    val dataState: String = "Unavailable"
) {
    val status: String get() = when { !available -> "Disconnected"; validated -> "Connected"; else -> "No internet" }
}
data class NetworkSnapshot(val wifi: NetworkInfo = NetworkInfo(), val mobile: NetworkInfo = NetworkInfo())
data class TrafficSample(val wifiDown: Double? = null, val wifiUp: Double? = null, val mobileDown: Double? = null, val mobileUp: Double? = null)
data class TrafficState(val current: TrafficSample = TrafficSample(), val history: List<TrafficSample> = emptyList(), val source: String = "Waiting for interface counters")
data class SessionState(
    val active: Boolean = false, val connecting: Boolean = false, val since: Long = 0,
    val detail: String = "Tap to connect", val paths: Int = 0, val vpn: Boolean = false,
    val preferred: String = "—", val error: String? = null
)
enum class UsagePeriod(val title: String) { TODAY("Today"), WEEK("This Week"), MONTH("This Month") }
data class AppUsage(val uid: Int, val name: String, val packageName: String?, val bytes: Long)
data class UsageState(val wifi: Long? = null, val mobile: Long? = null, val apps: List<AppUsage> = emptyList(), val loading: Boolean = false, val message: String? = "Allow usage access to see device data usage.") {
    val total: Long get() = (wifi ?: 0) + (mobile ?: 0)
}
data class ProbeResult(val latency: Double? = null, val jitter: Double? = null, val failures: Int = 0, val attempts: Int = 0) {
    val loss: Double? get() = if (attempts > 0) failures * 100.0 / attempts else null
}
data class PacketProbeResult(val sent: Int, val received: Int, val message: String? = null) {
    val loss: Double? get() = if (sent > 0) (sent - received) * 100.0 / sent else null
}
data class Diagnostics(val wifi: ProbeResult? = null, val mobile: ProbeResult? = null, val running: Boolean = false, val message: String? = null, val wifiPackets: PacketProbeResult? = null, val mobilePackets: PacketProbeResult? = null)
enum class TestPath(val title: String) { WIFI("Wi-Fi"), MOBILE("Mobile Data"), DEFAULT("System default") }
data class SpeedTestState(val running: Boolean = false, val phase: String = "Ready to test", val current: Double = 0.0, val ping: Double? = null, val download: Double? = null, val upload: Double? = null, val jitter: Double? = null, val error: String? = null, val route: String = "")
data class InstalledApp(val packageName: String, val name: String)
