package com.dualnet.optimizer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.ui.components.*
import com.dualnet.optimizer.ui.theme.*
import com.dualnet.optimizer.utils.bytes
import kotlin.math.roundToInt

@Composable
fun UsageScreen(state: UsageState, period: UsagePeriod, change: (UsagePeriod) -> Unit, back: () -> Unit, grant: () -> Unit, refresh: () -> Unit) {
    ScreenHeader("Data Usage", back) { IconButton(onClick = refresh) { Icon(Icons.Outlined.Refresh, "Refresh usage", tint = Muted) } }
    SegmentTabs(UsagePeriod.entries.map { it.title }, period.ordinal) { change(UsagePeriod.entries[it]) }
    Spacer(Modifier.height(15.dp))
    val fraction = if (state.total > 0 && state.wifi != null && state.mobile != null) (state.wifi!!.toFloat() / state.total) else null
    NeonCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.BarChart, null, tint = Blue, modifier = Modifier.size(22.dp)); Text("Total Data Usage", modifier = Modifier.padding(start = 7.dp), fontSize = 13.sp) }
                Text(if (state.wifi != null && state.mobile != null) bytes(state.total) else "—", color = Green, fontSize = 29.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
                Text("• Wi-Fi: ${bytes(state.wifi)}", color = Muted, fontSize = 12.sp)
                Text("• Mobile Data: ${bytes(state.mobile)}", color = Muted, fontSize = 12.sp)
            }
            Donut(fraction)
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(false, true).forEach { mobile ->
            NeonCard(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    GlowIcon(if (mobile) Icons.Outlined.SignalCellularAlt else Icons.Outlined.Wifi, if (mobile) Green else Cyan, 33.dp)
                    Column {
                        Text(if (mobile) "Mobile Data Usage" else "Wi-Fi Usage", fontSize = 11.sp)
                        Text(bytes(if (mobile) state.mobile else state.wifi), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                        Text(fraction?.let { "${((if (mobile) 1 - it else it) * 100).roundToInt()}% of total" } ?: "No breakdown", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    NeonCard(Modifier.fillMaxWidth()) {
        Text("Usage by App", style = MaterialTheme.typography.titleMedium)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 20.dp))
        if (state.apps.isEmpty() && !state.loading) Text("No app statistics available for this period.", color = Muted, modifier = Modifier.padding(top = 15.dp))
        val attributedTotal = state.apps.sumOf { it.bytes }.coerceAtLeast(1)
        state.apps.take(12).forEach { app ->
            val percent = (app.bytes.toFloat() / attributedTotal).coerceIn(0f, 1f)
            Row(Modifier.padding(top = 17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(app.packageName)
                Column(Modifier.weight(1f)) {
                    Row { Text(app.name, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1); Text(bytes(app.bytes), fontSize = 12.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = { percent }, modifier = Modifier.weight(1f).height(7.dp), color = Blue, trackColor = Outline, drawStopIndicator = {})
                        Text("${(percent * 100).roundToInt()}%", color = Muted, fontSize = 11.sp, modifier = Modifier.width(40.dp).padding(start = 7.dp))
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    state.message?.let { Note(it, if (state.wifi == null && state.mobile == null) "Open usage access" else null, grant) }
}

@Composable
fun AppIcon(packageName: String?) {
    val context = LocalContext.current
    val bitmap = remember(packageName) { packageName?.let { runCatching { context.packageManager.getApplicationIcon(it).toBitmap(64, 64).asImageBitmap() }.getOrNull() } }
    if (bitmap != null) Image(bitmap, null, Modifier.size(31.dp)) else GlowIcon(Icons.Outlined.Apps, Blue, 31.dp)
}
