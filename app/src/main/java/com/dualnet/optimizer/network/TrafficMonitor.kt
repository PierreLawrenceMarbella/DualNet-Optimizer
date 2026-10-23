package com.dualnet.optimizer.network

import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import com.dualnet.optimizer.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TrafficMonitor(networks: NetworkMonitor, scope: CoroutineScope) {
    private val _state = MutableStateFlow(TrafficState())
    val state = _state.asStateFlow()
    init {
        scope.launch(Dispatchers.IO) {
            var previous = emptyMap<String, Pair<Long, Long>>()
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val current = mutableMapOf<String, Pair<Long, Long>>()
                fun sample(info: NetworkInfo): Pair<Double?, Double?> {
                    val iface = info.interfaceName ?: return null to null
                    if (Build.VERSION.SDK_INT < 31) return null to null
                    val rx = TrafficStats.getRxBytes(iface); val tx = TrafficStats.getTxBytes(iface)
                    if (rx < 0 || tx < 0) return null to null
                    current[iface] = rx to tx
                    val old = previous[iface] ?: return null to null
                    val seconds = (now - last).coerceAtLeast(1) / 1000.0
                    return if (rx < old.first || tx < old.second) null to null else (rx - old.first) * 8 / seconds / 1e6 to (tx - old.second) * 8 / seconds / 1e6
                }
                val wifi = sample(networks.state.value.wifi); val mobile = sample(networks.state.value.mobile)
                val sample = TrafficSample(wifi.first, wifi.second, mobile.first, mobile.second)
                _state.value = TrafficState(sample, (_state.value.history + sample).takeLast(40), if (Build.VERSION.SDK_INT >= 31) "Interface estimates • ↓ download / ↑ upload in Network Booster" else "Live per-network counters require Android 12 or later. Run Speed Test to measure throughput.")
                previous = current; last = now
            }
        }
    }
}
