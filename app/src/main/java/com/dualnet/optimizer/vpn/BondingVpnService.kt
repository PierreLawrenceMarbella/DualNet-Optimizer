package com.dualnet.optimizer.vpn

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Network
import android.net.VpnService
import android.os.*
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.dualnet.optimizer.DualNetApplication
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.services.Notifications
import com.dualnet.optimizer.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Experimental IPv4 multipath VPN. An authenticated gateway is required before a TUN is established. */
class BondingVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val graph get() = (application as DualNetApplication).graph
    private var tun: ParcelFileDescriptor? = null
    private val paths = ConcurrentHashMap<String, TlsPath>()
    private val pending = ConcurrentHashMap<String, Job>()
    private var started = false
    private val writeLock = Any()
    private val cursor = AtomicInteger(0)
    private val sessionId = UUID.randomUUID().toString()
    private var since = 0L
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val notification = Notifications.build(this, "Connecting to secure gateway…", javaClass)
        if (Build.VERSION.SDK_INT >= 34) startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(101, notification)
        if (!started) { started = true; scope.launch { runTunnel() } }
        return START_NOT_STICKY
    }
    private suspend fun runTunnel() {
        try {
            val config = graph.preferences.preferences.first()
            check(config.gatewayConfigured) { "Configure your DualNet gateway and authentication token first." }
            check(validHost(config.serverHost) && config.serverPort in 1..65535 && validIpv4(config.dns)) { "Invalid gateway or DNS configuration." }
            withContext(Dispatchers.Main) { graph.networks.requestCellular() }
            graph.controller.session.value = SessionState(connecting = true, detail = "Authenticating gateway…", vpn = true)
            var noPathSince = SystemClock.elapsedRealtime()
            var lastError = "Gateway unreachable. Check the server, certificate, and token."
            while (currentCoroutineContext().isActive) {
                val snapshot = graph.networks.state.value
                val candidates = mapOf("wifi" to snapshot.wifi.network, "mobile" to snapshot.mobile.network)
                candidates.forEach { (label, network) ->
                    val existing = paths[label]
                    if (existing != null && existing.network != network) { paths.remove(label)?.close() }
                    if (network != null && paths[label] == null && pending[label]?.isActive != true) {
                        pending[label] = scope.launch {
                            var path: TlsPath? = null
                            try {
                                path = TlsPath.open(this@BondingVpnService, network, label, config, sessionId)
                                paths[label] = path
                                path.pump { packet ->
                                    val descriptor = tun?.fileDescriptor
                                    if (descriptor != null) synchronized(writeLock) { Os.write(descriptor, packet, 0, packet.size) }
                                }
                            } catch (e: CancellationException) { throw e } catch (_: Exception) { lastError = "A secure path failed. Verify gateway reachability, TLS certificate, and authentication." }
                            finally { if (paths[label] === path) paths.remove(label); path?.close() }
                        }
                    }
                }
                if (paths.isNotEmpty() && tun == null) {
                    val builder = Builder().setSession("DualNet • encrypted multipath").setMtu(config.mtu).setBlocking(false)
                        .addAddress("10.77.0.2", 32).addRoute("0.0.0.0", 0).addDnsServer(config.dns)
                    // No IPv6 address/family is enabled: Android blocks IPv6 for routed applications.
                    if (config.routedApps.isNotEmpty()) (config.routedApps + packageName).forEach { builder.addAllowedApplication(it) }
                    builder.setUnderlyingNetworks(paths.values.map { it.network }.toTypedArray())
                    tun = builder.establish() ?: error("VPN permission was revoked. Reconnect and allow VPN access.")
                    since = SystemClock.elapsedRealtime()
                    scope.launch { readTun() }
                }
                val count = paths.size
                setUnderlyingNetworks(paths.values.map { it.network }.toTypedArray())
                val detail = when (count) { 2 -> "Dual Network • 2 encrypted paths"; 1 -> "Single path fallback • reconnecting"; else -> "Reconnecting • traffic paused" }
                graph.controller.session.value = SessionState(active = count > 0 && tun != null, connecting = count == 0, since = since, detail = detail, paths = count, vpn = true, preferred = if (count == 2) "Wi-Fi + Mobile Data" else paths.keys.firstOrNull() ?: "—")
                val rates = graph.traffic.state.value.current
                val text = detail + if (graph.settings.value.showSpeed) " • Wi-Fi ${speed(rates.wifiDown)} / Mobile ${speed(rates.mobileDown)} Mbps ↓" else ""
                getSystemService(NotificationManager::class.java).notify(101, Notifications.build(this, text, javaClass))
                if (count > 0) noPathSince = SystemClock.elapsedRealtime()
                else if (SystemClock.elapsedRealtime() - noPathSince > 30_000 && (!config.connectionLock || tun == null)) error(lastError)
                delay(3000)
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            graph.controller.session.value = SessionState(error = e.message ?: "VPN connection failed")
            stopSelf()
        }
    }
    private suspend fun readTun() {
        val descriptor = tun?.fileDescriptor ?: return
        val buffer = ByteArray(FrameCodec.MAX_PACKET)
        try {
            while (currentCoroutineContext().isActive) {
                val poll = StructPollfd().apply { fd = descriptor; events = OsConstants.POLLIN.toShort() }
                if (Os.poll(arrayOf(poll), 1000) <= 0) continue
                val count = Os.read(descriptor, buffer, 0, buffer.size)
                if (count <= 0) continue
                val packet = buffer.copyOf(count)
                if (!FrameCodec.validIpv4(packet)) continue
                val available = paths.values.toList()
                if (available.isEmpty()) continue
                val offset = Math.floorMod(cursor.getAndIncrement(), available.size)
                // Bounded per-path queues avoid unbounded memory use when a radio stalls.
                for (index in available.indices) if (available[(offset + index) % available.size].outgoing.trySend(packet).isSuccess) break
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) { graph.controller.session.value = SessionState(error = "Tunnel interface closed. Reconnect to continue."); stopSelf() }
        }
    }
    override fun onRevoke() { stopSelf(); super.onRevoke() }
    override fun onDestroy() {
        paths.values.forEach { it.close() }; paths.clear()
        runCatching { tun?.close() }; tun = null; scope.cancel()
        graph.networks.releaseCellular()
        if (graph.controller.session.value.error == null) graph.controller.session.value = SessionState()
        super.onDestroy()
    }
}
