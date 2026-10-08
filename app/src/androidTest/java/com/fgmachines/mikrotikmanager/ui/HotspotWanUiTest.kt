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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HotspotWanUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun open(available:Boolean=true,arabic:Boolean=false) {
        compose.runOnUiThread { compose.activity.setContent { FgMikroTikTheme {
            CompositionLocalProvider(LocalLayoutDirection provides if(arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AdvancedSetupScreen(arabic,null,null,null,emptyList(),{},{},{},{},initialPanel="wizard",demo=true,demoWanAvailable=available)
            }
        } } }
        compose.waitForIdle()
    }
    private fun capture(name:String) {
        compose.waitForIdle();val i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync()
        android.os.SystemClock.sleep(750)
        val b=checkNotNull(i.uiAutomation.takeScreenshot())
        i.targetContext.openFileOutput(name,0).use { check(b.compress(Bitmap.CompressFormat.PNG,100,it)) };b.recycle()
    }
    @Test fun detectsEther1AndAllowsNext() {
        open();compose.onNodeWithText("Internet interface detected: ether1").performScrollTo().assertIsDisplayed()
        capture("hotspot-wan-auto-en.png")
        compose.onNodeWithText("Next").performScrollTo().assertIsEnabled()
    }
    @Test fun manualSelectorRequiresExplicitChoiceAndRejectsClientWan() {
        open();compose.onNodeWithTag("wanAuto").performScrollTo().performClick()
        compose.onNodeWithText("Next").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("wanManual").performScrollTo().performClick();capture("hotspot-wan-manual-en.png")
        compose.onNode(hasText("fg-clients") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("WAN and client interface are the same.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Next").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("wanManual").performScrollTo().performClick();compose.onNodeWithText("ether1").performClick()
        compose.onNodeWithText("Next").performScrollTo().assertIsEnabled()
    }
    @Test fun noRouteShowsFailureAndBlocksSetup() {
        open(false);compose.onNodeWithText("WAN detection failed. Choose Manual.").performScrollTo().assertIsDisplayed()
        capture("hotspot-wan-failed-en.png")
        compose.onNodeWithText("Next").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("wanAuto").performScrollTo().performClick()
        compose.onNodeWithTag("wanManual").performScrollTo().performClick();compose.onNodeWithText("ether1").performClick()
        compose.onNodeWithText("No active default route on selected WAN.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Next").performScrollTo().assertIsNotEnabled()
    }
    @Test fun arabicRtlDetectsEther1() {
        open(arabic=true);compose.onNodeWithText("تم اكتشاف واجهة الإنترنت تلقائيًا: ether1").performScrollTo().assertIsDisplayed()
        capture("hotspot-wan-auto-ar.png")
        compose.onNodeWithText("التالي").performScrollTo().assertIsEnabled()
    }
}
