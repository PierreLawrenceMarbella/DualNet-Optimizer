package com.dualnet.optimizer.services

import android.app.Service
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.dualnet.optimizer.DualNetApplication
import com.dualnet.optimizer.utils.speed
import kotlinx.coroutines.*

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph get() = (application as DualNetApplication).graph
    private var started = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val note = Notifications.build(this, "Checking connections…", javaClass)
        if (Build.VERSION.SDK_INT >= 34) startForeground(100, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(100, note)
        if (!started) {
            started = true
            graph.controller.start()
            scope.launch {
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
    override fun onDestroy() { scope.cancel(); graph.controller.stop(); super.onDestroy() }
}
