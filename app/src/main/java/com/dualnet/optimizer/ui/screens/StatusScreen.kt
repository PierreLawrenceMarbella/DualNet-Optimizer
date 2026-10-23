package com.dualnet.optimizer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.ui.components.*
import com.dualnet.optimizer.ui.theme.*
import com.dualnet.optimizer.utils.*

@Composable
fun StatusScreen(snapshot: NetworkSnapshot, back: () -> Unit, permissions: () -> Unit, settings: () -> Unit) {
    ScreenHeader("Network Status", back)
    listOf(false, true).forEach { mobile ->
        val info = if (mobile) snapshot.mobile else snapshot.wifi
        NeonCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                GlowIcon(if (mobile) Icons.Outlined.SignalCellularAlt else Icons.Outlined.Wifi, if (mobile) Green else Cyan)
                Column { Text(if (mobile) "Mobile Data" else "Wi-Fi", style = MaterialTheme.typography.titleMedium); Text(info.status, color = if (info.validated) Green else Muted, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(14.dp))
            DetailRow("Network", info.name)
            DetailRow("IP address", info.ip)
            if (!mobile) { DetailRow("Gateway", info.gateway); DetailRow("DNS", info.dns) } else DetailRow("Type", info.technology)
            DetailRow("Signal", info.signalDbm?.let { "$it dBm (${signalQuality(it, !mobile)})" } ?: "Unavailable")
            if (mobile) { DetailRow("Operator", info.carrier); DetailRow("Data state", info.dataState) }
        }
    }
    Note("Android may hide the Wi-Fi name or cellular details until permissions are granted. Wi-Fi name also requires Location to be on. Some carriers do not expose 5G or signal information.", "Allow network details", permissions)
    TextButton(onClick = settings) { Text("Open Wi-Fi & mobile data settings", color = Cyan) }
}
@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.width(88.dp)); Text(":  $value", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f)) }
}
