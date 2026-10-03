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
class BusinessToolsUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun waitText(text: String) { compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun tap(text: String) { val n=compose.onNodeWithText(text);runCatching { n.performScrollTo() };n.performClick() }
    private fun capture(name: String) { compose.waitForIdle();val i=InstrumentationRegistry.getInstrumentation();val b=i.uiAutomation.takeScreenshot();File(i.targetContext.filesDir,name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle() }
    @Test fun plansRenewalInvoiceExpenseReportsAndBranchFlow() {
        waitText("Subscribers & accounts");tap("Subscribers & accounts");waitText("Add subscriber");tap("Add subscriber")
        compose.onNodeWithText("Subscriber name").performTextInput("Expansion demo");tap("Save");waitText("Balance due: 0.00 EGP")
        tap("Business tools & plans");waitText("Plans & renewal");capture("business-tools-en.png");tap("Plans & renewal")
        waitText("Add plan");tap("Add plan");compose.onNodeWithText("Plan name").performTextInput("Demo 30 days")
        compose.onNodeWithText("Amount EGP").performTextInput("250.00")
        compose.activityRule.scenario.recreate();waitText("250.00");tap("Save");waitText("Demo 30 days")
        tap("Renew & invoice");compose.onNodeWithText("Collect now (optional) EGP").performTextInput("100.00");tap("Save")
        waitText("Renewed locally and invoice recorded. Router unchanged.")
        compose.onNodeWithContentDescription("Language").performClick();waitText("باقة جديدة");capture("business-plans-ar.png")
        compose.onNodeWithContentDescription("رجوع").performClick();waitText("الفواتير");tap("الفواتير")
        waitText("المحصّل عند الإصدار: 100.00 EGP");capture("business-invoices-ar.png")
        compose.onNodeWithContentDescription("رجوع").performClick();tap("المصروفات");waitText("مصروف جديد");tap("مصروف جديد")
        compose.onNodeWithText("فئة المصروف").performTextInput("Demo office");compose.onNodeWithText("المبلغ EGP").performTextInput("25.00")
        compose.onNodeWithText("البيان / السبب").performTextInput("Demo paper");tap("حفظ");waitText("Demo office")
        compose.onNodeWithContentDescription("رجوع").performClick();tap("التقارير والتصدير");waitText("عرض الفترة")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("التحصيل: 100.00 EGP"));waitText("التحصيل: 100.00 EGP")
        compose.onNodeWithText("المصروفات: 25.00 EGP").assertExists();capture("business-reports-ar.png")
        compose.onNodeWithContentDescription("رجوع").performClick();tap("الفواتير");waitText("إلغاء الفاتورة");tap("إلغاء الفاتورة")
        compose.onNodeWithText("البيان / السبب").performTextInput("Demo cancellation");tap("حفظ");waitText("ملغاة بقيد عكسي")
        compose.onNodeWithContentDescription("رجوع").performClick();tap("الفروع");waitText("فرع جديد");tap("فرع جديد")
        compose.onNodeWithText("اسم الفرع").performTextInput("Demo secondary");tap("حفظ");waitText("Demo secondary")
        tap("Demo secondary");waitText("مشترك جديد")
        compose.onNodeWithText("Expansion demo").assertDoesNotExist()
        // Restore the main branch so other independent UI tests keep their own baseline.
        tap("إدارة الأعمال والباقات");waitText("Main branch");tap("Main branch");waitText("مشترك جديد")
    }
}
