package com.dualnet.optimizer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import android.content.Intent
import com.dualnet.optimizer.services.MonitorService
import com.dualnet.optimizer.vpn.BondingVpnService
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.network.MeasurementClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** These integration checks transfer real data. Run on a connected emulator/test device. */
@RunWith(AndroidJUnit4::class)
class NetworkIntegrationTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DualNetApplication
    @Test fun realHttpsSpeedTestReturnsMeasuredValues(): Unit = runBlocking {
        val snapshot = withTimeout(20000) { app.graph.networks.state.first { it.wifi.validated || it.mobile.validated } }
        val network = snapshot.wifi.network ?: snapshot.mobile.network
        val phases = mutableListOf<String>()
        var result = SpeedTestState()
        val client = MeasurementClient()
        try {
            withTimeout(120000) { client.speedTest(network, "Instrumented test network") { result = it; phases += it.phase } }
            assertFalse(result.running)
            assertTrue((result.ping ?: 0.0) > 0)
            assertTrue((result.download ?: 0.0) > 0)
            assertTrue((result.upload ?: 0.0) > 0)
            assertTrue(phases.contains("Measuring download…")); assertTrue(phases.contains("Measuring upload…"))
            android.util.Log.i("DualNetTest", "Real HTTPS result: ping=${result.ping} down=${result.download} up=${result.upload} Mbps")
        } finally { client.cancel() }
    }
    @Test fun credentialsAndModePersistThroughDataStore(): Unit = runBlocking {
        val original = app.graph.preferences.preferences.first()
        try {
            app.graph.preferences.mode(ConnectionMode.WIFI)
            app.graph.preferences.gateway("test.invalid", 4433, "instrumentation-only-token-1234567890", 1400, 30)
            val read = app.graph.preferences.preferences.first()
            assertEquals(ConnectionMode.WIFI, read.mode)
            assertEquals("instrumentation-only-token-1234567890", read.token)
            val raw = java.io.File(app.filesDir, "datastore/dualnet_preferences.preferences_pb").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(raw.contains("instrumentation-only-token-1234567890"))
        } finally {
            app.graph.preferences.mode(original.mode)
            app.graph.preferences.gateway(original.serverHost, original.serverPort, original.token, original.mtu, original.probeInterval)
        }
    }
    @Test fun usageDenialReturnsExplicitLimitation(): Unit = runBlocking {
        val repository = app.graph.usage
        val state = repository.load(UsagePeriod.TODAY)
        if (!repository.hasAccess()) { assertNull(state.wifi); assertNull(state.mobile); assertNotNull(state.message) }
        else { assertTrue(state.wifi != null || state.mobile != null); assertNotNull(state.message) }
    }
    @Test fun foregroundSelectionStartsAndStops(): Unit = runBlocking {
        val original = app.graph.preferences.preferences.first()
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                app.graph.preferences.mode(ConnectionMode.WIFI)
                app.graph.settings.first { it.mode == ConnectionMode.WIFI }
                withTimeout(15000) { app.graph.networks.state.first { it.wifi.validated } }
                app.startForegroundService(Intent(app, MonitorService::class.java))
                val active = withTimeout(15000) { app.graph.controller.session.first { it.active } }
                assertFalse(active.vpn); assertEquals("Wi-Fi", active.preferred)
                app.stopService(Intent(app, MonitorService::class.java))
                withTimeout(10000) { app.graph.controller.session.first { !it.active } }
            } finally { app.stopService(Intent(app, MonitorService::class.java)); app.graph.preferences.mode(original.mode) }
        }
    }
    @Test fun missingGatewayCannotActivateVpn(): Unit = runBlocking {
        val original = app.graph.preferences.preferences.first()
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                app.graph.preferences.gateway("", 4433, "", 1400, 30)
                app.startForegroundService(Intent(app, BondingVpnService::class.java))
                val rejected = withTimeout(10000) { app.graph.controller.session.first { it.error != null } }
                assertFalse(rejected.active); assertEquals(0, rejected.paths)
                assertTrue(rejected.error!!.contains("gateway"))
            } finally {
                app.stopService(Intent(app, BondingVpnService::class.java))
                app.graph.preferences.gateway(original.serverHost, original.serverPort, original.token, original.mtu, original.probeInterval)
            }
        }
    }
}
