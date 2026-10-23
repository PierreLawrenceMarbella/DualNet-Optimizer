package com.dualnet.optimizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.ui.theme.*

/** Representative reference values are confined to Compose previews. */
@Composable
private fun PreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    DualNetTheme { Column(Modifier.background(Navy).padding(16.dp), content = content) }
}
@Preview(widthDp = 400, heightDp = 820, showBackground = true)
@Composable
private fun HomePreview() = PreviewFrame {
    HomeScreen(
        NetworkSnapshot(NetworkInfo(available = true, validated = true, name = "Boarding House", ip = "192.168.1.10"), NetworkInfo(available = true, validated = true, name = "Smart", ip = "10.45.23.18", technology = "5G", carrier = "Smart")),
        TrafficState(TrafficSample(48.2, 3.2, 36.7, 1.1), List(40) { TrafficSample(20.0 + kotlin.math.sin(it * .55) * 10 + it % 9, mobileDown = 12.0 + kotlin.math.sin(it * .48) * 8) }),
        SessionState(active = true, vpn = true, paths = 2), ConnectionMode.DUAL, {}, {})
}
@Preview(widthDp = 400, heightDp = 820)
@Composable
private fun ModesPreview() = PreviewFrame { ModesScreen(ConnectionMode.DUAL, true, {}, {}, {}) }
@Preview(widthDp = 400, heightDp = 820)
@Composable
private fun UsagePreview() = PreviewFrame {
    UsageScreen(UsageState(1_620_000_000, 860_000_000, listOf(AppUsage(1, "YouTube", null, 1_240_000_000), AppUsage(2, "Chrome", null, 432_000_000), AppUsage(3, "Google Drive", null, 286_000_000), AppUsage(4, "Facebook", null, 154_000_000), AppUsage(5, "Others", null, 368_000_000)), message = null), UsagePeriod.TODAY, {}, {}, {}, {})
}
@Preview(widthDp = 400, heightDp = 820)
@Composable
private fun StatusPreview() = PreviewFrame { StatusScreen(NetworkSnapshot(NetworkInfo(available = true, validated = true, name = "Boarding House", ip = "192.168.1.10", gateway = "192.168.1.1", dns = "8.8.8.8", signalDbm = -45), NetworkInfo(available = true, validated = true, name = "Smart 5G", ip = "10.45.23.18", signalDbm = -85, technology = "5G", carrier = "SMART", dataState = "Active")), {}, {}, {}) }
@Preview(widthDp = 400, heightDp = 820)
@Composable
private fun SpeedPreview() = PreviewFrame { SpeedScreen(SpeedTestState(current = 152.4, ping = 18.0, download = 152.4, upload = 48.7), true, {}, { _, _ -> }, {}, {}) }
@Preview(widthDp = 400, heightDp = 820)
@Composable
private fun ToolsPreview() = PreviewFrame { ToolsScreen({}, {}) }
@Preview(widthDp = 400, heightDp = 820)
@Composable
private fun SettingsPreview() = PreviewFrame { SettingsScreen(Preferences(autoStart = true, showSpeed = true), {}, { _, _ -> }, {}, {}) }
