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
    private fun scrollNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }
    private fun scrollText(value: String) = scrollNode(hasText(value))
    private fun scrollTag(value: String) = scrollNode(hasTestTag(value))
    private fun capture(name:String) {
        compose.waitForIdle();val i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync()
        android.os.SystemClock.sleep(750)
        val b=checkNotNull(i.uiAutomation.takeScreenshot())
        i.targetContext.openFileOutput(name,0).use { check(b.compress(Bitmap.CompressFormat.PNG,100,it)) };b.recycle()
    }
    @Test fun detectsEther1AndAllowsNext() {
        open();scrollText("Internet interface detected: ether1").assertIsDisplayed()
        capture("hotspot-wan-auto-en.png")
        scrollText("Next").assertIsEnabled()
    }
    @Test fun manualSelectorRequiresExplicitChoiceAndRejectsClientWan() {
        open();scrollTag("wanAuto").performClick()
        scrollText("Next").assertIsNotEnabled()
        scrollTag("wanManual").performClick();capture("hotspot-wan-manual-en.png")
        compose.onNode(hasText("fg-clients") and hasAnyAncestor(isPopup())).performClick()
        scrollText("WAN and client interface are the same.").assertIsDisplayed()
        scrollText("Next").assertIsNotEnabled()
        scrollTag("wanManual").performClick();compose.onNode(hasText("ether1") and hasAnyAncestor(isPopup())).performClick()
        scrollText("Next").assertIsEnabled()
    }
    @Test fun noRouteShowsFailureAndBlocksSetup() {
        open(false);scrollText("WAN detection failed. Choose Manual.").assertIsDisplayed()
        capture("hotspot-wan-failed-en.png")
        scrollText("Next").assertIsNotEnabled()
        scrollTag("wanAuto").performClick()
        scrollTag("wanManual").performClick();compose.onNode(hasText("ether1") and hasAnyAncestor(isPopup())).performClick()
        scrollText("No active default route on selected WAN.").assertIsDisplayed()
        scrollText("Next").assertIsNotEnabled()
    }
    @Test fun arabicRtlDetectsEther1() {
        open(arabic=true);scrollText("تم اكتشاف واجهة الإنترنت تلقائيًا: ether1").assertIsDisplayed()
        capture("hotspot-wan-auto-ar.png")
        scrollText("التالي").assertIsEnabled()
    }
}
