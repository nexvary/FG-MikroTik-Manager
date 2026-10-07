package com.fgmachines.mikrotikmanager.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fgmachines.mikrotikmanager.MainActivity
import com.fgmachines.mikrotikmanager.data.DiscoveredRouter
import com.fgmachines.mikrotikmanager.data.RouterConnectionSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        instrumentation.targetContext.openFileOutput(name, 0).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    @Test fun allNineRoutersCanBeSelectedAndConnectRemainsVisibleInBothLanguages() {
        val routers = (1..9).map { DiscoveredRouter("Fixture router $it", "192.168.88.${it+10}") }
        for (arabic in listOf(false, true)) {
            var submitted: RouterConnectionSettings? = null
            compose.runOnUiThread { compose.activity.setContent {
                FgMikroTikTheme { CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    ConnectionScreen(false, false, routers, null, arabic, {}, {}, {}, { submitted = it }, {})
                } }
            } }
            compose.onNodeWithTag("connectRouterButton").assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithText(if (arabic) "اختيار راوتر (9)" else "Choose router (9)").performScrollTo().performClick()
            compose.onNodeWithTag("discoveredRouterList").performScrollToNode(hasText("Fixture router 9"))
            compose.onNodeWithText("Fixture router 9").assertIsDisplayed()
            capture(if (arabic) "connection-discovery-ar.png" else "connection-discovery-en.png")
            compose.onNodeWithText("Fixture router 9").performClick()
            compose.onNodeWithTag("discoveredRouterList").assertDoesNotExist()
            compose.onNodeWithTag("routerHostField").assertTextContains("192.168.88.19")
            compose.onNodeWithTag("connectRouterButton").assertIsDisplayed().assertIsEnabled()
            capture(if (arabic) "connection-fixed-ar.png" else "connection-fixed-en.png")
            compose.onNodeWithTag("connectRouterButton").performClick()
            assertEquals("192.168.88.19", submitted?.host)
            assertEquals("admin", submitted?.username)
        }
    }
}
