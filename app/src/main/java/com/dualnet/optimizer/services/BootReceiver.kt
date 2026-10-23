package com.dualnet.optimizer.services

import android.content.*
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.dualnet.optimizer.DualNetApplication
import com.dualnet.optimizer.data.ConnectionMode
import com.dualnet.optimizer.vpn.BondingVpnService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val graph = (context.applicationContext as DualNetApplication).graph
        graph.scope.launch {
            try {
                val config = withTimeout(5000) { graph.preferences.preferences.first() }
                if (config.autoStart) {
                    val service = if (config.mode == ConnectionMode.DUAL) {
                        if (!config.gatewayConfigured || VpnService.prepare(context) != null) return@launch
                        BondingVpnService::class.java
                    } else MonitorService::class.java
                    runCatching { ContextCompat.startForegroundService(context, Intent(context, service)) }
                }
            } finally { pending.finish() }
        }
    }
}
