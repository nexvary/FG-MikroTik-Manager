package com.fgmachines.mikrotikmanager.business

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fgmachines.mikrotikmanager.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BusinessUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun waitText(text: String) {
        compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun tap(text: String) { val node=compose.onNodeWithText(text); runCatching { node.performScrollTo() }; node.performClick() }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun subscriberPaymentReversalAndArabicFlow() {
        waitText("Subscribers & accounts")
        tap("Subscribers & accounts")
        waitText("Add subscriber"); tap("Add subscriber")
        compose.onNodeWithText("Subscriber name").performTextInput("Demo subscriber")
        tap("Save")
        waitText("Balance due: 0.00 EGP")
        tap("Add charge")
        compose.onNodeWithText("Amount EGP").performTextInput("1000.10")
        compose.onNodeWithText("Description / reason").performTextInput("Demo monthly charge")
        tap("Save"); waitText("Balance due: 1000.10 EGP")
        tap("Record payment")
        compose.onNodeWithText("Amount EGP").performTextInput("400.05")
        compose.onNodeWithText("Description / reason").performTextInput("Demo cash payment")
        tap("Save"); waitText("Balance due: 600.05 EGP")
        capture("business-en.png")
        compose.onAllNodesWithText("Correct with reversal").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Description / reason").performTextInput("Demo correction")
        tap("Save"); waitText("Balance due: 1000.10 EGP")
        compose.onNodeWithContentDescription("Language").performClick()
        waitText("الرصيد المستحق: 1000.10 EGP")
        capture("business-ar.png")
        compose.onNodeWithContentDescription("رجوع").performClick()
        waitText("مشترك جديد")
        compose.onNodeWithText("Demo subscriber").assertExists()
        capture("business-subscribers-ar.png")
    }
}
