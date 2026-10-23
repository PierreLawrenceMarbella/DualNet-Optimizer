package com.dualnet.optimizer.services

import android.app.Service
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.dualnet.optimizer.DualNetApplication
import com.dualnet.optimizer.data.ConnectionMode
import com.dualnet.optimizer.data.SessionState
import com.dualnet.optimizer.utils.speed
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph get() = (application as DualNetApplication).graph
    private var started = false
    private var startupError: String? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val note = Notifications.build(this, "Checking connections…", javaClass)
        if (Build.VERSION.SDK_INT >= 34) startForeground(100, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(100, note)
        if (!started) {
            started = true
            scope.launch {
                // DataStore may not have populated the UI's initial StateFlow yet on boot.
                // Always load the persisted mode before selecting or requesting a network.
                val config = try { graph.preferences.preferences.first() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { startupError = "Unable to read connection settings. Reopen the app and try again."; stopSelf(); return@launch }
                if (config.mode == ConnectionMode.DUAL) {
                    startupError = "Select Dual Network Mode in the app and connect with VPN permission."
                    stopSelf(); return@launch
                }
                graph.controller.start(config.mode)
                while (isActive) {
                    val traffic = graph.traffic.state.value.current
                    val text = graph.controller.session.value.detail + if (graph.settings.value.showSpeed) " • Wi-Fi ${speed(traffic.wifiDown)} / Mobile ${speed(traffic.mobileDown)} Mbps ↓" else ""
                    getSystemService(NotificationManager::class.java).notify(100, Notifications.build(this@MonitorService, text, javaClass))
                    delay(2000)
                }
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); graph.controller.stop(); startupError?.let { graph.controller.session.value = SessionState(error = it) }; super.onDestroy() }
}
