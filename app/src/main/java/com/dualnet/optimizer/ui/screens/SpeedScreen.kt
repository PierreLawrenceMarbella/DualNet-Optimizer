package com.dualnet.optimizer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.ui.components.*
import com.dualnet.optimizer.ui.theme.*
import com.dualnet.optimizer.utils.speed

@Composable
fun SpeedScreen(state: SpeedTestState, bonded: Boolean, back: () -> Unit, start: (Boolean, TestPath) -> Unit, cancel: () -> Unit, setup: () -> Unit) {
    var dual by rememberSaveable { mutableStateOf(false) }
    var pathIndex by rememberSaveable { mutableIntStateOf(0) }
    ScreenHeader("Speed Test", back)
    SegmentTabs(listOf("Dual Network", "Single Network"), if (dual) 0 else 1) { if (!state.running) dual = it == 0 }
    if (!dual) {
        Spacer(Modifier.height(10.dp))
        SegmentTabs(TestPath.entries.map { it.title }, pathIndex) { if (!state.running) pathIndex = it }
    }
    SpeedGauge(state.current)
    Text(state.phase, color = Muted, fontSize = 12.sp, modifier = Modifier.alignCenterWidth().padding(vertical = 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(Triple("Ping", state.ping, "ms"), Triple("Download", state.download, "Mbps"), Triple("Upload", state.upload, "Mbps")).forEach { (title, value, unit) ->
            NeonCard(Modifier.weight(1f)) { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, color = Muted, fontSize = 12.sp); Text(speed(value), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp)); Text(unit, color = Muted, fontSize = 12.sp) } }
        }
    }
    Spacer(Modifier.height(18.dp))
    Button(onClick = { if (state.running) cancel() else start(dual, TestPath.entries[pathIndex]) }, modifier = Modifier.fillMaxWidth().height(53.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), enabled = state.running || !dual || bonded) { Text(if (state.running) "Cancel Test" else "Start Test", fontSize = 16.sp) }
    Spacer(Modifier.height(14.dp))
    if (dual && !bonded) Note("Dual Network testing requires an active VPN with both paths connected.", "Configure gateway", setup)
    state.error?.let { Text(it, color = Yellow, modifier = Modifier.padding(vertical = 10.dp)) }
    Text("A test transfers up to 24 MB through Cloudflare. Mobile data charges may apply. Ping is HTTPS response time; results include protocol overhead and are not a gaming latency guarantee.", color = Muted, fontSize = 12.sp)
    if (state.route.isNotBlank()) Text("Route: ${state.route} • jitter ${speed(state.jitter)} ms", color = Cyan, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
}
private fun Modifier.alignCenterWidth() = fillMaxWidth()
