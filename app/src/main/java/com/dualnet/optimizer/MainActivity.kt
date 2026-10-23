package com.dualnet.optimizer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dualnet.optimizer.navigation.DualNetApp
import com.dualnet.optimizer.ui.theme.DualNetTheme
import com.dualnet.optimizer.viewmodel.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            val model: MainViewModel = viewModel()
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == Activity.RESULT_OK) model.startVpn() else model.vpnDenied() }
            val networkPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.refresh(); if (it.values.any { granted -> !granted }) model.message("Some network details remain hidden. You can grant access in Android app settings.") }
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (!it) model.message("Notifications are off. Android still lists an active foreground service in its task manager.") }
            val lifecycle = LocalLifecycleOwner.current
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
                lifecycle.lifecycle.addObserver(observer)
                onDispose { lifecycle.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(Unit) { model.events.collect { event -> when (event) { is UiEvent.Message -> scope.launch { snackbar.showSnackbar(event.text) }; is UiEvent.VpnPermission -> vpnPermission.launch(event.intent) } } }
            fun openSettings(action: String) { runCatching { startActivity(Intent(action)) }.onFailure { model.message("This settings page is unavailable on your device. Open it from Android Settings.") } }
            DualNetTheme {
                DualNetApp(model, snackbar,
                    permissions = { networkPermissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE)) },
                    usageAccess = { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                    internetSettings = { openSettings(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS) },
                    privateDns = { openSettings("android.settings.PRIVATE_DNS_SETTINGS") },
                    vpnSettings = { openSettings(Settings.ACTION_VPN_SETTINGS) },
                    notificationPermission = { if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }
        }
    }
}
