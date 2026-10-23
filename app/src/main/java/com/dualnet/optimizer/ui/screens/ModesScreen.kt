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

@Composable
fun ModesScreen(mode: ConnectionMode, configured: Boolean, select: (ConnectionMode) -> Unit, back: () -> Unit, setup: () -> Unit) {
    ScreenHeader("Connection Mode", back)
    ConnectionMode.entries.forEach { option ->
        NeonCard(Modifier.fillMaxWidth().padding(bottom = 14.dp), selected = option == mode, onClick = { select(option) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlowIcon(when (option) { ConnectionMode.DUAL -> Icons.Outlined.Link; ConnectionMode.WIFI -> Icons.Outlined.Wifi; ConnectionMode.MOBILE -> Icons.Outlined.SignalCellularAlt; ConnectionMode.SMART -> Icons.Outlined.Memory }, if (option == ConnectionMode.DUAL || option == ConnectionMode.SMART) Blue else Green)
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(option.title, style = MaterialTheme.typography.titleMedium)
                    if (option == ConnectionMode.SMART) Text("Recommended", color = Green, fontSize = 11.sp)
                    Text(option.description, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp))
                }
                Icon(if (option == mode) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (option == mode) "Selected" else "Select ${option.title}", tint = if (option == mode) Blue else Muted, modifier = Modifier.size(25.dp))
            }
        }
    }
    NeonCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Info, null, tint = Blue); Text("Mode Information", color = Blue, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp)) }
        Text(when (mode) {
            ConnectionMode.DUAL -> "Dual Network Mode sends IP packets over two encrypted paths to your compatible DualNet gateway. Speed depends on both networks and the server. ${if (!configured) "Bonding is unavailable until you configure a gateway." else "Both Wi-Fi and mobile data must be available to start."}"
            ConnectionMode.SMART -> "Smart Mode periodically measures HTTPS response times and selects an available connection for DualNet's own requests. It does not route other apps or combine bandwidth."
            ConnectionMode.WIFI -> "DualNet's tests and diagnostics use Wi-Fi only. Other apps continue to use Android's routing. Enable Wi-Fi before connecting."
            ConnectionMode.MOBILE -> "DualNet's tests and diagnostics use mobile data only. Carrier data charges may apply. Other apps continue to use Android's routing."
        }, color = Muted, modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyLarge)
        if (mode == ConnectionMode.DUAL) TextButton(onClick = setup) { Text("Configure bonding gateway", color = Cyan) }
    }
}
