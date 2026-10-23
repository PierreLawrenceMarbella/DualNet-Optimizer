package com.dualnet.optimizer

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun shot(name: String) {
        compose.waitForIdle()
        // Compose's virtual clock settles before SurfaceFlinger presents the real frame.
        android.os.SystemClock.sleep(700)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
    private fun back() { compose.onNodeWithContentDescription("Back").performClick(); compose.waitForIdle() }
    @Test fun allSevenScreensAndSettingsAreReachable() {
        compose.onNodeWithText("DualNet Optimizer").assertIsDisplayed(); shot("01-home")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Auto Start on Boot").assertIsDisplayed(); shot("07-settings"); back()
        compose.onNodeWithTag("nav_modes").performClick()
        compose.onNodeWithText("Wi-Fi Only").performClick()
        compose.onNodeWithText("Mode Information").performScrollTo().assertIsDisplayed(); shot("02-modes")
        compose.onNodeWithText("Smart Mode").performScrollTo().performClick()
        compose.onNodeWithTag("nav_usage").performClick()
        compose.onNodeWithText("This Week").performClick(); compose.onNodeWithText("Usage by App").assertExists(); shot("03-usage")
        compose.onNodeWithTag("nav_tools").performClick(); shot("06-tools")
        compose.onNodeWithText("Network Status").performScrollTo().performClick()
        compose.onNodeWithText("Allow network details").performScrollTo().assertIsDisplayed(); shot("04-status"); back()
        compose.onNodeWithText("Speed Test").performScrollTo().performClick()
        compose.onNodeWithText("Start Test").performScrollTo().assertIsDisplayed(); shot("05-speed"); back()
        listOf("App Routing", "DNS Settings", "Network Booster", "Connection Lock", "Advanced Settings").forEach { title ->
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithContentDescription("Back").assertExists()
            back()
        }
    }
    @Test fun dualModeCannotPretendToConnectWithoutGateway() {
        compose.onNodeWithTag("nav_modes").performClick()
        compose.onNodeWithText("Dual Network Mode").performClick()
        compose.onNodeWithTag("nav_home").performClick()
        compose.onNodeWithContentDescription("Connect").performClick()
        compose.onNodeWithText("Disconnected").assertExists()
        compose.onNodeWithTag("nav_modes").performClick()
        compose.onNodeWithText("Smart Mode").performScrollTo().performClick()
    }
}
