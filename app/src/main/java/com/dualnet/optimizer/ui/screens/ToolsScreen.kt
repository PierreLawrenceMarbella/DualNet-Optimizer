package com.dualnet.optimizer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.*
import com.dualnet.optimizer.ui.components.*
import com.dualnet.optimizer.ui.theme.*

@Composable
fun ToolsScreen(back: () -> Unit, navigate: (String) -> Unit) {
    ScreenHeader("Tools", back)
    ToolRow("App Routing", "Choose which apps use dual network.", Icons.Outlined.GridView) { navigate("routing") }
    ToolRow("DNS Settings", "Use faster and more secure DNS.", Icons.Outlined.Dns) { navigate("dns") }
    ToolRow("Network Booster", "Optimize connection for streaming and downloads.", Icons.Outlined.RocketLaunch) { navigate("booster") }
    ToolRow("Connection Lock", "Keep VPN connected automatically.", Icons.Outlined.Lock, Yellow) { navigate("lock") }
    ToolRow("Advanced Settings", "More network configuration options.", Icons.Outlined.Settings) { navigate("advanced") }
    Spacer(Modifier.height(15.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = { navigate("status") }) { Text("Network Status", color = Cyan) }
        TextButton(onClick = { navigate("speed") }) { Text("Speed Test", color = Cyan) }
    }
}
@Composable
fun ToolRow(title: String, subtitle: String, icon: ImageVector, color: Color = Blue, click: () -> Unit) {
    NeonCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = click) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            GlowIcon(icon, color, 43.dp)
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp)) }
            Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.size(19.dp))
        }
    }
}
