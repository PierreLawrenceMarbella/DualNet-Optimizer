package com.dualnet.optimizer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.dualnet.optimizer.data.*
import com.dualnet.optimizer.ui.components.*
import com.dualnet.optimizer.ui.theme.*
import com.dualnet.optimizer.utils.*

@Composable
fun RoutingScreen(apps: List<InstalledApp>, selected: Set<String>, save: (Set<String>) -> Unit, back: () -> Unit) {
    ScreenHeader("App Routing", back)
    Note(if (selected.isEmpty()) "All apps will use the VPN when connected. Select apps below to restrict routing. DualNet is always included for tunnel testing." else "${selected.size} selected apps will use the VPN. Other apps use Android's default connection. Reconnect to apply changes.")
    TextButton(onClick = { save(emptySet()) }) { Text("Route all apps", color = Cyan) }
    apps.forEach { app ->
        Row(Modifier.fillMaxWidth().clickable { save(if (app.packageName in selected) selected - app.packageName else selected + app.packageName) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName)
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) { Text(app.name, fontSize = 14.sp); Text(app.packageName, fontSize = 10.sp, color = Muted) }
            Checkbox(app.packageName in selected, { save(if (it) selected + app.packageName else selected - app.packageName) })
        }
    }
}

@Composable
fun DnsScreen(dns: String, back: () -> Unit, save: (String) -> Unit, privateDns: () -> Unit) {
    var address by rememberSaveable(dns) { mutableStateOf(dns) }
    ScreenHeader("DNS Settings", back)
    Note("This resolver is applied inside the encrypted VPN on your next connection. Outside the VPN, Android manages DNS. DNS settings do not guarantee faster speeds.")
    Spacer(Modifier.height(18.dp))
    listOf("1.1.1.1" to "Cloudflare", "8.8.8.8" to "Google", "9.9.9.9" to "Quad9").forEach { (value, name) ->
        NeonCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), selected = address == value, onClick = { address = value }) { Text("$name • $value") }
    }
    OutlinedTextField(address, { address = it }, label = { Text("Custom IPv4 resolver") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = !validIpv4(address))
    Button(onClick = { save(address) }, enabled = validIpv4(address), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Save DNS") }
    TextButton(onClick = privateDns) { Text("Open Android Private DNS", color = Cyan) }
    Text("The gateway forwards DNS from its public network. For end-to-end encrypted DNS outside the tunnel, configure Android Private DNS.", color = Muted, fontSize = 12.sp)
}

@Composable
fun BoosterScreen(diagnostics: Diagnostics, traffic: TrafficState, back: () -> Unit, run: () -> Unit, modes: () -> Unit) {
    ScreenHeader("Network Booster", back)
    Note("Check latency, jitter, and failed requests before streaming or gaming. Smart Mode can use these measurements for DualNet's own traffic. No artificial speed boost is applied.")
    Spacer(Modifier.height(16.dp))
    listOf(false, true).forEach { mobile ->
        val result = if (mobile) diagnostics.mobile else diagnostics.wifi
        val packets = if (mobile) diagnostics.mobilePackets else diagnostics.wifiPackets
        NeonCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(if (mobile) "Mobile Data" else "Wi-Fi", style = MaterialTheme.typography.titleMedium, color = if (mobile) Green else Cyan)
            DetailRow("Latency", "${speed(result?.latency)} ms")
            DetailRow("Jitter", "${speed(result?.jitter)} ms")
            DetailRow("Probe loss", "${speed(result?.loss)}% (${result?.attempts ?: 0} requests)")
            DetailRow("UDP loss", packets?.loss?.let { "${speed(it)}% (${packets.received}/${packets.sent} replies)" } ?: "Requires gateway")
            packets?.message?.let { Text(it, color = Yellow, fontSize = 11.sp) }
            DetailRow("Download", "${speed(if (mobile) traffic.current.mobileDown else traffic.current.wifiDown)} Mbps")
            DetailRow("Upload", "${speed(if (mobile) traffic.current.mobileUp else traffic.current.wifiUp)} Mbps")
        }
    }
    Button(onClick = run, enabled = !diagnostics.running, modifier = Modifier.fillMaxWidth()) { Text(if (diagnostics.running) "Measuring…" else "Run Network Diagnostics") }
    TextButton(onClick = modes) { Text("Choose connection mode", color = Cyan) }
    Text(diagnostics.message ?: "HTTPS probes include server response time. Configure a DualNet gateway with its UDP port open to measure actual round-trip packet loss. Failed HTTPS requests are reported separately.", color = Muted, fontSize = 12.sp)
}

@Composable
fun LockScreen(enabled: Boolean, back: () -> Unit, change: (Boolean) -> Unit, systemSettings: () -> Unit) {
    ScreenHeader("Connection Lock", back)
    NeonCard(Modifier.fillMaxWidth()) { SwitchRow("Reconnect VPN automatically", Icons.Outlined.Lock, enabled, "Retry failed paths while the service is running.", change) }
    Spacer(Modifier.height(16.dp))
    Note("With Connection Lock on, an established tunnel keeps retrying after both paths are lost and routed traffic waits. For protection across app stops or device restarts, enable Always-on VPN and Block connections without VPN in Android's VPN settings. Configure and test your gateway first.", "Open Android VPN settings", systemSettings)
}

@Composable
fun AdvancedScreen(prefs: Preferences, back: () -> Unit, save: (String, Int, String, Int, Int) -> Unit) {
    var host by rememberSaveable(prefs.serverHost) { mutableStateOf(prefs.serverHost) }
    var port by rememberSaveable(prefs.serverPort) { mutableStateOf(prefs.serverPort.toString()) }
    // Do not put the credential in saved-instance state.
    var token by remember(prefs.token) { mutableStateOf(prefs.token) }
    var mtu by rememberSaveable(prefs.mtu) { mutableStateOf(prefs.mtu.toString()) }
    var interval by rememberSaveable(prefs.probeInterval) { mutableStateOf(prefs.probeInterval.toString()) }
    ScreenHeader("Advanced Settings", back)
    Note("Use the included DualNet DN1 Linux gateway with a valid public TLS certificate. A generic VPN server is not compatible. Bonding is unavailable until authentication succeeds.")
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(host, { host = it.trim() }, label = { Text("Gateway hostname") }, placeholder = { Text("vpn.example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(port, { port = it }, label = { Text("TLS port") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
    OutlinedTextField(token, { token = it.trim() }, label = { Text("Authentication token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
    OutlinedTextField(mtu, { mtu = it }, label = { Text("Tunnel MTU (1280–1500)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
    OutlinedTextField(interval, { interval = it }, label = { Text("Smart probe interval (15–300 seconds)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
    val valid = validHost(host) && port.toIntOrNull() in 1..65535 && token.length in 32..512 && mtu.toIntOrNull() in 1280..1500 && interval.toIntOrNull() in 15..300
    Button(onClick = { save(host, port.toInt(), token, mtu.toInt(), interval.toInt()) }, enabled = valid, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("Save Configuration") }
    Text("Use a random token of at least 32 characters. Credentials are encrypted with Android Keystore. This experimental IPv4 tunnel uses two TLS paths; it can increase reordering or latency and does not guarantee a speed increase. Reconnect after changing settings.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
}

@Composable
fun AboutScreen(back: () -> Unit) {
    ScreenHeader("About", back)
    NeonCard(Modifier.fillMaxWidth()) {
        GlowIcon(Icons.Outlined.RocketLaunch, Cyan, 60.dp)
        Text("DualNet Optimizer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        Text("Version 1.0.0", color = Green)
        Text("Wi-Fi + Mobile Data • Faster • More Stable", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
        Text("Monitor real networks, compare connections, and connect to your own encrypted multipath gateway. Smart Mode and single-network modes control DualNet traffic. Other applications are routed only while the VPN is connected.", color = Muted)
        Text("Privacy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
        Text("No analytics or advertising SDKs. Usage statistics stay on your device. Tests and diagnostics contact Cloudflare, which receives your public IP address. Your gateway handles routed traffic; use a server you trust. TLS protects traffic between your device and gateway. HTTPS remains important beyond the gateway.", color = Muted, modifier = Modifier.padding(top = 8.dp))
    }
}
