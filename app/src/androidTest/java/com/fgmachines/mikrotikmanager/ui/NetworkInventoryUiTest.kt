package com.fgmachines.mikrotikmanager.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fgmachines.mikrotikmanager.MainActivity
import com.fgmachines.mikrotikmanager.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkInventoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun twoDevicesArabicEnglishReadOnlyRefreshAndBack() {
        var refreshed = 0; var backed = false; var mutations = 0
        val snapshot = RouterMenuSnapshot(RouterAdminModule.NETWORK_DEVICES, "network-devices", listOf(
            mapOf(".id" to "mac:AA:BB:CC:DD:EE:01", "name" to "AP one", "address" to "192.168.88.2", "mac-address" to "AA:BB:CC:DD:EE:01", "sources" to "Neighbor • DHCP • ARP", "interface" to "ether2"),
            mapOf(".id" to "mac:AA:BB:CC:DD:EE:02", "name" to "AP two", "address" to "192.168.88.3", "mac-address" to "AA:BB:CC:DD:EE:02", "sources" to "DHCP • ARP", "interface" to "ether3")
        ), listOf("ip/neighbor"))
        for (arabic in listOf(false, true)) {
            compose.runOnUiThread { compose.activity.setContent {
                FgMikroTikTheme { CompositionLocalProvider(LocalLayoutDirection provides if(arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    RouterAdminScreen(arabic, RouterAdminGroup.NETWORK, false, false, RouterAdminModule.NETWORK_DEVICES,
                        snapshot, null, null, {}, {}, { refreshed++ }, { backed = true },
                        { mutations++ }, { _,_,_,_ -> mutations++ }, { _,_ -> mutations++ }, { _,_ -> mutations++ }, { mutations++ }, {})
                } }
            } }
            compose.onNodeWithText(if(arabic) "أجهزة الشبكة والأكسسات" else "Network devices & APs").assertIsDisplayed()
            compose.onNodeWithText("AP one").assertExists()
            compose.onNodeWithText("AP two").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Add").assertDoesNotExist()
            compose.onNodeWithText(if(arabic) "تعديل" else "Edit").assertDoesNotExist()
            compose.onNodeWithText(if(arabic) "حذف" else "Delete").assertDoesNotExist()
            compose.onNodeWithContentDescription("Refresh").performClick()
            compose.waitForIdle()
            val image=compose.onRoot().captureToImage().asAndroidBitmap()
            val filename=if(arabic) "network-devices-ar.png" else "network-devices-en.png"
            InstrumentationRegistry.getInstrumentation().targetContext.openFileOutput(filename,0).use { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it)) }
            image.recycle()
            compose.onAllNodesWithText(if(arabic) "نسخ عنوان IP" else "Copy IP address").onFirst().performScrollTo().performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            assertTrue(backed); backed=false
        }
        assertEquals(2, refreshed); assertEquals(0, mutations)
    }
}
