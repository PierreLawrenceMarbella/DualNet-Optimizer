package com.dualnet.optimizer.viewmodel

import android.app.Application
import android.content.Intent
import android.net.NetworkCapabilities
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dualnet.optimizer.DualNetApplication
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.network.MeasurementClient
import com.dualnet.optimizer.services.MonitorService
import com.dualnet.optimizer.services.Notifications
import com.dualnet.optimizer.utils.speed
import com.dualnet.optimizer.vpn.BondingVpnService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface UiEvent { data class Message(val text: String) : UiEvent; data class VpnPermission(val intent: Intent) : UiEvent }
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as DualNetApplication).graph
    val settings = graph.settings
    val networks = graph.networks.state
    val traffic = graph.traffic.state
    val session = graph.controller.session.asStateFlow()
    val diagnostics = graph.controller.diagnostics.asStateFlow()
    val usage = MutableStateFlow(UsageState())
    val period = MutableStateFlow(UsagePeriod.TODAY)
    val speedTest = MutableStateFlow(SpeedTestState())
    val apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    private val client = MeasurementClient()
    private var speedJob: Job? = null
    private var usageJob: Job? = null
    private var connectJob: Job? = null
    init { refreshUsage(); viewModelScope.launch { apps.value = graph.usage.installedApps() } }
    fun message(text: String) { _events.tryEmit(UiEvent.Message(text)) }
    fun refresh() { graph.networks.refreshPermissions(); refreshUsage() }
    fun refreshUsage() { usageJob?.cancel(); usageJob = viewModelScope.launch { usage.value = usage.value.copy(loading = true); usage.value = graph.usage.load(period.value) } }
    fun usagePeriod(value: UsagePeriod) { period.value = value; refreshUsage() }
    fun mode(value: ConnectionMode) { stop(); viewModelScope.launch { graph.preferences.mode(value) } }
    fun toggle(key: String, value: Boolean) { viewModelScope.launch { graph.preferences.toggle(key, value) } }
    fun dns(value: String) { viewModelScope.launch { graph.preferences.dns(value); message("DNS saved. Reconnect the VPN to apply it.") } }
    fun apps(value: Set<String>) { viewModelScope.launch { graph.preferences.apps(value) } }
    fun gateway(host: String, port: Int, token: String, mtu: Int, interval: Int) { viewModelScope.launch { runCatching { graph.preferences.gateway(host, port, token, mtu, interval) }.onSuccess { message("Gateway settings saved. Reconnect to apply changes.") }.onFailure { message("Unable to store credentials securely.") } } }
    fun toggleConnection() {
        if (session.value.active || session.value.connecting) { stop(); return }
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            val config = graph.preferences.preferences.first()
            if (config.mode == ConnectionMode.DUAL) {
                if (!config.gatewayConfigured) { message("Bonding unavailable. Open Tools → Advanced Settings to configure a DualNet gateway, or select Smart Mode."); return@launch }
                graph.networks.requestCellular()
                if (!networks.value.wifi.available || !networks.value.mobile.available) {
                    message("Waiting for Wi-Fi and mobile data. Both are required to start dual mode.")
                    withTimeoutOrNull(8000) { networks.first { it.wifi.available && it.mobile.available } }
                }
                if (!networks.value.wifi.available || !networks.value.mobile.available) { graph.networks.releaseCellular(); message("Enable both Wi-Fi and mobile data, then reconnect. Android or your carrier may restrict simultaneous connections."); return@launch }
                val permission = VpnService.prepare(getApplication())
                if (permission != null) _events.emit(UiEvent.VpnPermission(permission)) else startVpn()
            } else startService(false)
        }
    }
    fun startVpn() = startService(true)
    fun vpnDenied() { graph.networks.releaseCellular(); message("VPN permission was not granted. No traffic was rerouted.") }
    private fun startService(vpn: Boolean) {
        runCatching { ContextCompat.startForegroundService(getApplication(), Intent(getApplication(), if (vpn) BondingVpnService::class.java else MonitorService::class.java)) }
            .onFailure { message("Android could not start the connection service. Open the app and try again.") }
    }
    fun stop() {
        connectJob?.cancel()
        getApplication<Application>().stopService(Intent(getApplication(), MonitorService::class.java))
        getApplication<Application>().stopService(Intent(getApplication(), BondingVpnService::class.java))
        graph.controller.stop()
    }
    fun diagnose() { viewModelScope.launch { graph.networks.requestCellular(); delay(1200); try { graph.controller.diagnose(includeUdp = true) } finally { if (!session.value.active && !session.value.connecting) graph.networks.releaseCellular() } } }
    fun test(dual: Boolean, path: TestPath) {
        if (speedJob?.isActive == true) return
        speedJob = viewModelScope.launch {
            try {
                if (path == TestPath.MOBILE && !dual) { graph.networks.requestCellular(); withTimeoutOrNull(8000) { networks.first { it.mobile.available } } }
                val selected = if (dual) {
                    check(session.value.vpn && session.value.paths == 2) { "Connect both VPN paths before testing Dual Network." }
                    graph.networks.manager.allNetworks.firstOrNull { graph.networks.manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true } ?: error("VPN network is unavailable.")
                } else when (path) {
                    TestPath.WIFI -> networks.value.wifi.network ?: error("Wi-Fi is unavailable.")
                    TestPath.MOBILE -> networks.value.mobile.network ?: error("Mobile data is unavailable.")
                    TestPath.DEFAULT -> graph.controller.preferredNetwork ?: graph.networks.manager.activeNetwork ?: error("No network is available.")
                }
                val route = if (dual) "DualNet VPN • two paths" else if (path == TestPath.DEFAULT && graph.controller.preferredNetwork != null) "${settings.value.mode.title} → ${session.value.preferred}" else path.title
                client.speedTest(selected, route) { speedTest.value = it }
                if (settings.value.notifications) Notifications.result(getApplication(), "↓ ${speed(speedTest.value.download)} Mbps • ↑ ${speed(speedTest.value.upload)} Mbps")
            } catch (e: CancellationException) { speedTest.value = speedTest.value.copy(running = false, phase = "Test cancelled"); throw e }
            catch (e: Exception) { speedTest.value = speedTest.value.copy(running = false, phase = "Test could not finish", error = e.message ?: "Network request failed.") }
            finally { client.cancel(); if (!session.value.active && !session.value.connecting) graph.networks.releaseCellular() }
        }
    }
    fun cancelTest() { speedJob?.cancel(); client.cancel() }
    override fun onCleared() { client.cancel(); super.onCleared() }
}
