package com.dualnet.optimizer.network

import android.net.Network
import android.os.SystemClock
import com.dualnet.optimizer.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** App-scoped selection only. It deliberately does not bind the whole process or affect other apps. */
class ConnectionController(private val monitor: NetworkMonitor, private val preferences: StateFlow<Preferences>, private val scope: CoroutineScope) {
    val session = MutableStateFlow(SessionState())
    val diagnostics = MutableStateFlow(Diagnostics())
    @Volatile var preferredNetwork: Network? = null
        private set
    private var loop: Job? = null
    private val probes = MeasurementClient()
    fun start() {
        stop()
        if (preferences.value.mode == ConnectionMode.DUAL) return
        if (preferences.value.mode != ConnectionMode.WIFI) monitor.requestCellular()
        session.value = SessionState(connecting = true, detail = "Checking available connections…")
        loop = scope.launch {
            var lastProbe = 0L
            while (isActive) {
                val mode = preferences.value.mode
                val snapshot = monitor.state.value
                val wifi = snapshot.wifi.takeIf { it.validated }?.network
                val cell = snapshot.mobile.takeIf { it.validated }?.network
                if (mode == ConnectionMode.SMART && SystemClock.elapsedRealtime() - lastProbe > preferences.value.probeInterval * 1000L) {
                    diagnose()
                    lastProbe = SystemClock.elapsedRealtime()
                }
                preferredNetwork = when (mode) {
                    ConnectionMode.WIFI -> wifi
                    ConnectionMode.MOBILE -> cell
                    ConnectionMode.SMART -> chooseNetwork(wifi, cell, diagnostics.value)
                    ConnectionMode.DUAL -> null
                }
                val label = when (preferredNetwork) { null -> "—"; wifi -> "Wi-Fi"; else -> "Mobile Data" }
                val old = session.value
                session.value = SessionState(active = preferredNetwork != null, connecting = preferredNetwork == null, since = if (preferredNetwork == null) 0 else if (old.active) old.since else SystemClock.elapsedRealtime(), detail = if (preferredNetwork == null) "Waiting for an internet connection. Enable the selected network." else "${mode.title} • DualNet app traffic only", preferred = label)
                delay(1500)
            }
        }
    }
    private fun chooseNetwork(wifi: Network?, cell: Network?, d: Diagnostics): Network? {
        if (wifi == null) return cell
        if (cell == null) return wifi
        val w = d.wifi?.latency ?: Double.MAX_VALUE; val c = d.mobile?.latency ?: Double.MAX_VALUE
        // A 20% margin avoids switching on small timing fluctuations.
        return if (preferredNetwork == cell) { if (w < c * .8) wifi else cell } else { if (c < w * .8) cell else wifi }
    }
    suspend fun diagnose(includeUdp: Boolean = false) {
        if (diagnostics.value.running) return
        diagnostics.value = diagnostics.value.copy(running = true)
        try {
            val snapshot = monitor.state.value
            coroutineScope {
                val wifi = async { snapshot.wifi.network?.let { probes.probe(it) } }
                val cell = async { snapshot.mobile.network?.let { probes.probe(it) } }
                val config = preferences.value
                suspend fun packets(network: Network?) = if (includeUdp && config.gatewayConfigured && network != null) {
                    try { UdpDiagnostics.probe(network, config) } catch (e: CancellationException) { throw e } catch (_: Exception) { PacketProbeResult(0, 0, "UDP diagnostics unavailable on this path") }
                } else null
                val wifiPackets = async { packets(snapshot.wifi.network) }; val mobilePackets = async { packets(snapshot.mobile.network) }
                diagnostics.value = Diagnostics(wifi.await(), cell.await(), message = "HTTPS response times include server processing. UDP loss measures missing round-trip replies to your gateway; endpoint or firewall failures can also cause loss.", wifiPackets = wifiPackets.await(), mobilePackets = mobilePackets.await())
            }
        } finally { diagnostics.value = diagnostics.value.copy(running = false) }
    }
    fun stop() { loop?.cancel(); loop = null; probes.cancel(); preferredNetwork = null; monitor.releaseCellular(); session.value = SessionState() }
}
