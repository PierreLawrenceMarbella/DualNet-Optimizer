package com.dualnet.optimizer.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.*
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.*
import androidx.core.content.ContextCompat
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.data.NetworkInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
class NetworkMonitor(private val context: Context, private val scope: CoroutineScope) {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val phone = context.getSystemService(TelephonyManager::class.java)
    private val _state = MutableStateFlow(NetworkSnapshot())
    val state = _state.asStateFlow()
    private val capabilities = mutableMapOf<Network, NetworkCapabilities>()
    private var cellularRequest: ConnectivityManager.NetworkCallback? = null
    private var phoneListening = false
    private var cellularDbm: Int? = null
    private val listener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signal: SignalStrength) {
            cellularDbm = if (Build.VERSION.SDK_INT >= 29) signal.cellSignalStrengths.firstOrNull()?.dbm?.takeIf { it in -150..-20 } else null
            refresh()
        }
        override fun onDataConnectionStateChanged(state: Int, networkType: Int) { refresh() }
    }
    private val callback = if (Build.VERSION.SDK_INT >= 31) createCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) else createCallback(0)

    private fun createCallback(flags: Int): ConnectivityManager.NetworkCallback = if (Build.VERSION.SDK_INT >= 31) object : ConnectivityManager.NetworkCallback(flags) {
        override fun onAvailable(network: Network) { refresh() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { scope.launch { capabilities[network] = caps; refresh() } }
        override fun onLinkPropertiesChanged(network: Network, link: LinkProperties) { refresh() }
        override fun onLost(network: Network) { scope.launch { capabilities.remove(network); refresh() } }
    } else object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { refresh() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { scope.launch { capabilities[network] = caps; refresh() } }
        override fun onLinkPropertiesChanged(network: Network, link: LinkProperties) { refresh() }
        override fun onLost(network: Network) { scope.launch { capabilities.remove(network); refresh() } }
    }
    init {
        manager.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
        refreshPermissions()
        scope.launch { while (isActive) { refresh(); delay(3000) } }
    }
    fun refreshPermissions() {
        if (!phoneListening && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            phoneListening = runCatching { phone?.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE); true }.getOrDefault(false)
        }
        refresh()
    }
    /** A user-initiated request keeps cellular available alongside Wi-Fi; it never toggles mobile data. */
    fun requestCellular() {
        if (cellularRequest != null) return
        val request = object : ConnectivityManager.NetworkCallback() { override fun onAvailable(network: Network) { refresh() }; override fun onLost(network: Network) { refresh() } }
        runCatching { manager.requestNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), request); cellularRequest = request }
    }
    fun releaseCellular() { cellularRequest?.let { runCatching { manager.unregisterNetworkCallback(it) } }; cellularRequest = null }
    fun refresh() { scope.launch { _state.value = NetworkSnapshot(read(false), read(true)) } }
    private fun read(mobile: Boolean): NetworkInfo {
        val transport = if (mobile) NetworkCapabilities.TRANSPORT_CELLULAR else NetworkCapabilities.TRANSPORT_WIFI
        val network = manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.let { c -> c.hasTransport(transport) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } == true } ?: return NetworkInfo()
        val caps = capabilities[network] ?: manager.getNetworkCapabilities(network) ?: return NetworkInfo()
        val link = manager.getLinkProperties(network)
        val wifi = if (!mobile) (if (Build.VERSION.SDK_INT >= 29) caps.transportInfo as? WifiInfo else null) ?: runCatching { wifiManager.connectionInfo }.getOrNull() else null
        fun visibleSsid(value: String?) = value?.trim('"')?.takeUnless { it == "<unknown ssid>" || it.isBlank() }
        val ssid = visibleSsid(wifi?.ssid) ?: visibleSsid(runCatching { wifiManager.connectionInfo.ssid }.getOrNull()) ?: "Name unavailable"
        val technology = runCatching { when (phone?.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> "Unavailable"
        } }.getOrDefault("Permission needed")
        val carrier = runCatching { phone?.networkOperatorName?.takeIf { it.isNotBlank() } }.getOrNull() ?: "Unavailable"
        return NetworkInfo(network, true, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED), if (mobile) carrier else ssid,
            link?.linkAddresses?.map { it.address }?.sortedBy { it.address.size }?.firstOrNull()?.hostAddress ?: "—",
            link?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: "—",
            link?.dnsServers?.joinToString(", ") { it.hostAddress ?: "" }?.ifEmpty { "—" } ?: "—", link?.interfaceName,
            if (mobile) cellularDbm else wifi?.rssi?.takeIf { it in -126..-1 }, technology, carrier,
            runCatching { when (phone?.dataState) { TelephonyManager.DATA_CONNECTED -> "Active"; TelephonyManager.DATA_CONNECTING -> "Connecting"; TelephonyManager.DATA_SUSPENDED -> "Suspended"; else -> "Inactive" } }.getOrDefault("Unavailable"))
    }
}
