package com.dualnet.optimizer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.*
import com.dualnet.optimizer.data.Preferences
import com.dualnet.optimizer.ui.components.*
import com.dualnet.optimizer.ui.theme.*

@Composable
fun SettingsScreen(prefs: Preferences, back: () -> Unit, toggle: (String, Boolean) -> Unit, about: () -> Unit, message: (String) -> Unit) {
    ScreenHeader("Settings", back)
    NeonCard(Modifier.fillMaxWidth()) {
        SwitchRow("Auto Start on Boot", Icons.Outlined.Alarm, prefs.autoStart, "Automatically connect when device restarts.") { toggle("autoStart", it) }
        HorizontalDivider(color = Outline)
        SwitchRow("Show Speed on Status Bar", Icons.Outlined.Speed, prefs.showSpeed) { toggle("showSpeed", it) }
        HorizontalDivider(color = Outline)
        SwitchRow("Notification", Icons.Outlined.Notifications, prefs.notifications) { toggle("notifications", it) }
        HorizontalDivider(color = Outline)
        SettingRow("Theme", "Dark", Icons.Outlined.Palette, { message("Dark is the reference theme for version 1.0.0.") })
        HorizontalDivider(color = Outline)
        SettingRow("Language", "English", Icons.Outlined.Language, { message("English is the available language in version 1.0.0.") })
    }
    Spacer(Modifier.height(16.dp))
    NeonCard(Modifier.fillMaxWidth()) {
        SettingRow("About", "", Icons.Outlined.Info, about)
        HorizontalDivider(color = Outline)
        SettingRow("Version", "1.0.0", Icons.Outlined.Verified, { message("DualNet Optimizer 1.0.0") }, arrow = false)
    }
    Spacer(Modifier.height(16.dp))
    Text("Live speed appears in the ongoing connection notification. Android requires a foreground notification while a connection runs. The Notification toggle controls optional alerts. Boot startup depends on Android and device power settings.", color = Muted, fontSize = 12.sp)
}
@Composable
fun SwitchRow(title: String, icon: ImageVector, checked: Boolean, subtitle: String? = null, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) { Text(title, fontSize = 13.sp); subtitle?.let { Text(it, fontSize = 11.sp, color = Muted, lineHeight = 16.sp) } }
        Switch(checked, change, colors = SwitchDefaults.colors(checkedTrackColor = Blue, checkedThumbColor = White), modifier = Modifier.height(32.dp))
    }
}
@Composable
private fun SettingRow(title: String, value: String, icon: ImageVector, click: () -> Unit, arrow: Boolean = true) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Icon(icon, null, modifier = Modifier.size(22.dp)); Text(title, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(value, fontSize = 12.sp, color = Muted)
        if (arrow) Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.size(17.dp))
    }
}
