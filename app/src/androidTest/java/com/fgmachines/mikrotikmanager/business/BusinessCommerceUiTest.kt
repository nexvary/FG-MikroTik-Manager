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
class BusinessCommerceUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun tap(text: String){compose.waitUntil(15000){runCatching{compose.onNodeWithText(text).assertIsEnabled();true}.getOrDefault(false)};val n=compose.onNodeWithText(text);runCatching{n.performScrollTo()};n.performClick()}
    private fun waitText(text:String){compose.waitUntil(15000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun capture(name:String){compose.waitForIdle();val i=InstrumentationRegistry.getInstrumentation();val b=i.uiAutomation.takeScreenshot();File(i.targetContext.filesDir,name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    @Test fun saleWithInstaPayAndArabicReceiptList(){
        tap("Subscribers & accounts");tap("Add subscriber");compose.onNodeWithText("Subscriber name").performTextInput("Commerce demo");tap("Save");waitText("Balance due: 0.00 EGP")
        tap("Business tools & plans");tap("Sales & receipts");tap("New sale")
        compose.onNodeWithText("Item / service").performTextInput("Demo installation")
        compose.onNodeWithText("Unit price EGP").performTextInput("120.00");tap("Add line")
        compose.onNodeWithText("Collect now (optional)").performTextInput("50.00")
        tap("Payment method: Cash");tap("InstaPay");compose.onNodeWithText("Payment reference (optional)").performTextInput("DEMO-REF")
        compose.activityRule.scenario.recreate();waitText("DEMO-REF");tap("Save");waitText("120.00 EGP")
        compose.onNodeWithContentDescription("Language").performClick();waitText("المبيعات والإيصالات");capture("business-sales-ar.png")
        tap("إلغاء البيع");compose.onNodeWithText("البيان / السبب").performTextInput("Demo correction");tap("حفظ");waitText("ملغاة")
    }
}
