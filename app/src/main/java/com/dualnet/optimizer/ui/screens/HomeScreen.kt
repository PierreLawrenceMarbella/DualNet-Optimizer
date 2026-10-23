package com.dualnet.optimizer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.dualnet.optimizer.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.ui.components.*
import com.dualnet.optimizer.ui.theme.*
import com.dualnet.optimizer.utils.*
import kotlinx.coroutines.delay
import android.os.SystemClock

@Composable
fun HomeScreen(networks: NetworkSnapshot, traffic: TrafficState, session: SessionState, mode: ConnectionMode, navigate: (String) -> Unit, toggle: () -> Unit) {
    var elapsed by remember { mutableLongStateOf(0) }
    LaunchedEffect(session.active, session.since) { while (true) { elapsed = if (session.active) ((SystemClock.elapsedRealtime() - session.since) / 1000).coerceAtLeast(0) else 0; delay(1000) } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_launcher), null, modifier = Modifier.size(45.dp))
        Column(Modifier.weight(1f).padding(start = 9.dp)) {
            Text("DualNet Optimizer", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Wi-Fi + Mobile Data • Faster • More Stable", fontSize = 10.sp, color = Muted, maxLines = 1)
        }
        IconButton(onClick = { navigate("settings") }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Settings, "Settings", Modifier.size(23.dp)) }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PowerRing(session.active, session.connecting, if (session.active) duration(elapsed) else "Ready when you are", toggle) }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NetworkMini("Wi-Fi", networks.wifi, false, Modifier.weight(1f)) { navigate("status") }
        NetworkMini("Mobile Data", networks.mobile, true, Modifier.weight(1f)) { navigate("status") }
    }
    Spacer(Modifier.height(14.dp))
    NeonCard(Modifier.fillMaxWidth(), onClick = { navigate("modes") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowIcon(Icons.Outlined.Link, Green)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(if (mode == ConnectionMode.DUAL) "Bonding Mode" else mode.title, style = MaterialTheme.typography.titleMedium)
                Text(when { session.vpn && session.paths == 2 -> "Using Wi-Fi + Mobile Data together"; session.vpn -> "One path • reconnecting second network"; mode == ConnectionMode.DUAL -> "Gateway setup required"; else -> "App traffic • ${session.preferred}" }, color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.ChevronRight, "Connection Mode", tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
    NeonCard(Modifier.fillMaxWidth(), onClick = { navigate("speed") }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Live Network Speed", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("● Wi-Fi", color = Blue, fontSize = 10.sp)
            Spacer(Modifier.width(12.dp))
            Text("● Mobile Data", color = Green, fontSize = 10.sp)
        }
        Spacer(Modifier.height(9.dp))
        TrafficGraph(traffic.history)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SpeedLabel(traffic.current.wifiDown, "Wi-Fi", Blue)
            SpeedLabel(traffic.current.mobileDown, "Mobile Data", Green)
        }
        Text("Download • tap to run a speed test", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
    }
    Text(session.error ?: session.detail, color = if (session.error == null) Muted else Yellow, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
    Text(traffic.source, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
}

@Composable
private fun NetworkMini(title: String, info: NetworkInfo, mobile: Boolean, modifier: Modifier, click: () -> Unit) {
    NeonCard(modifier.fillMaxHeight(), onClick = click) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowIcon(if (mobile) Icons.Outlined.SignalCellularAlt else Icons.Outlined.Wifi, if (mobile) Green else Cyan, 35.dp)
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(info.status, fontSize = 12.sp, color = if (info.validated) Green else Muted)
                Text(if (mobile && info.available) "${info.technology} • ${info.carrier}" else info.name, fontSize = 11.sp, color = Muted, maxLines = 2)
                Text(info.ip, fontSize = 11.sp, color = Muted, maxLines = 1)
            }
        }
    }
}
@Composable
private fun SpeedLabel(value: Double?, label: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) { Text(speed(value), fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(" Mbps", fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp)) }
        Text("• $label", color = color, fontSize = 12.sp)
    }
}
