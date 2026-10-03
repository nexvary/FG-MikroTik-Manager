package com.fgmachines.mikrotikmanager.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fgmachines.mikrotikmanager.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationsUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun tap(text:String){compose.waitUntil(15000){runCatching{compose.onNodeWithText(text).assertIsEnabled();true}.getOrDefault(false)};val n=compose.onNodeWithText(text);runCatching{n.performScrollTo()};n.performClick();compose.waitForIdle()}
    private fun waitText(text:String){compose.waitUntil(10000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun capture(name:String){val i=InstrumentationRegistry.getInstrumentation();val b=i.uiAutomation.takeScreenshot();i.targetContext.openFileOutput(name,0).use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    @Test fun routerProfilesTeamWalletAndArchiveScreens(){
        compose.onNodeWithText("Router IP or hostname").performTextInput("192.168.88.1")
        tap("Router center");tap("Save current connection fields")
        compose.onNodeWithText("Router name").performTextInput("Navigation demo")
        compose.onNodeWithText("Branch / group label").performTextInput("Cairo")
        tap("Save");waitText("Navigation demo • Cairo");capture("router-center-en.png")
        tap("Select");compose.onNodeWithText("192.168.88.1").assertExists()
        tap("Subscribers & accounts");tap("Business tools & plans");tap("Staff & resellers")
        waitText("Add staff or reseller");tap("Add staff or reseller")
        compose.onNodeWithText("Name").performTextInput("Demo reseller")
        tap("Save");waitText("Demo reseller • RESELLER");tap("Wallet & commissions")
        waitText("Balance: 0.00 EGP");tap("DEPOSIT")
        compose.onNodeWithText("Note / reason").performTextInput("Demo deposit")
        compose.onNodeWithText("Amount").performTextInput("100.00");tap("Save");waitText("Balance: 100.00 EGP");capture("team-wallet-en.png")
        tap("Back");tap("Back");tap("Transfer voucher archive")
        waitText("Protect & transfer voucher archive");capture("voucher-archive-en.png");tap("Back")
        compose.onNodeWithText("Staff & resellers").assertExists()
    }
}
