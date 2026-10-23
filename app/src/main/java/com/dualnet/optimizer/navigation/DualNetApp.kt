package com.dualnet.optimizer.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.dualnet.optimizer.ui.screens.*
import com.dualnet.optimizer.ui.theme.*
import com.dualnet.optimizer.viewmodel.MainViewModel

private data class Tab(val route: String, val title: String, val icon: ImageVector)
private val tabs = listOf(Tab("home", "Home", Icons.Outlined.Home), Tab("modes", "Modes", Icons.Outlined.DashboardCustomize), Tab("usage", "Usage", Icons.Outlined.BarChart), Tab("tools", "Tools", Icons.Outlined.Settings))

@Composable
fun DualNetApp(model: MainViewModel, snackbar: SnackbarHostState, permissions: () -> Unit, usageAccess: () -> Unit, internetSettings: () -> Unit, privateDns: () -> Unit, vpnSettings: () -> Unit, notificationPermission: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val prefs by model.settings.collectAsStateWithLifecycle()
    val networks by model.networks.collectAsStateWithLifecycle()
    val traffic by model.traffic.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val usage by model.usage.collectAsStateWithLifecycle()
    val period by model.period.collectAsStateWithLifecycle()
    val speed by model.speedTest.collectAsStateWithLifecycle()
    val diagnostics by model.diagnostics.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    val navigate: (String) -> Unit = { target -> nav.navigate(target) { launchSingleTop = true } }
    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate("home") { launchSingleTop = true } }
    Scaffold(containerColor = Navy, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (route in tabs.map { it.route }) {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xFF071322), tonalElevation = 0.dp, modifier = Modifier.border(.5.dp, Outline)) {
                tabs.forEach { tab ->
                    NavigationBarItem(modifier = Modifier.testTag("nav_${tab.route}"), selected = route == tab.route, onClick = { nav.navigate(tab.route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(tab.icon, tab.title, Modifier.size(25.dp)) }, label = { Text(tab.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Blue, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Blue.copy(alpha = .09f)))
                }
            }
        }
    }) { padding ->
        NavHost(nav, "home", modifier = Modifier.padding(padding), enterTransition = { fadeIn() + slideInHorizontally { it / 14 } }, exitTransition = { fadeOut() }, popEnterTransition = { fadeIn() }, popExitTransition = { fadeOut() }) {
            composable("home") { Page { HomeScreen(networks, traffic, session, prefs.mode, navigate) { if (!session.active && !session.connecting && prefs.notifications) notificationPermission(); model.toggleConnection() } } }
            composable("modes") { Page { ModesScreen(prefs.mode, prefs.gatewayConfigured, model::mode, back) { navigate("advanced") } } }
            composable("usage") { Page { UsageScreen(usage, period, model::usagePeriod, back, usageAccess, model::refreshUsage) } }
            composable("status") { Page { StatusScreen(networks, back, permissions, internetSettings) } }
            composable("speed") { Page { SpeedScreen(speed, session.vpn && session.paths == 2, back, model::test, model::cancelTest) { navigate("advanced") } } }
            composable("tools") { Page { ToolsScreen(back, navigate) } }
            composable("settings") { Page { SettingsScreen(prefs, back, { key, value -> model.toggle(key, value); if (value && (key == "notifications" || key == "showSpeed")) notificationPermission() }, { navigate("about") }, model::message) } }
            composable("routing") { Page { RoutingScreen(apps, prefs.routedApps, model::apps, back) } }
            composable("dns") { Page { DnsScreen(prefs.dns, back, model::dns, privateDns) } }
            composable("booster") { Page { BoosterScreen(diagnostics, traffic, back, model::diagnose) { navigate("modes") } } }
            composable("lock") { Page { LockScreen(prefs.connectionLock, back, { model.toggle("connectionLock", it) }, vpnSettings) } }
            composable("advanced") { Page { AdvancedScreen(prefs, back, model::gateway) } }
            composable("about") { Page { AboutScreen(back) } }
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), content = content)
    }
}
