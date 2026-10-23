package com.dualnet.optimizer

import android.app.Application
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DualNetApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var graph: AppGraph
        private set
    override fun onCreate() { super.onCreate(); graph = AppGraph(this, scope) }
}
class AppGraph(val app: Application, val scope: CoroutineScope) {
    val preferences = PreferenceRepository(app)
    val settings = preferences.preferences.stateIn(scope, SharingStarted.Eagerly, Preferences())
    val networks = NetworkMonitor(app, scope)
    val traffic = TrafficMonitor(networks, scope)
    val usage = UsageRepository(app)
    val controller = ConnectionController(networks, settings, scope)
}
