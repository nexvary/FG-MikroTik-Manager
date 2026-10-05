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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DnsProtectionUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun capture(name:String){
        compose.waitForIdle();val b=compose.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.openFileOutput(name,0).use{check(b.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
    @Test fun disconnectedScreenEnglishArabicScrollAndBack() {
        var back=false
        for(arabic in listOf(false,true)){
            compose.runOnUiThread{compose.activity.setContent{FgMikroTikTheme{CompositionLocalProvider(LocalLayoutDirection provides if(arabic)LayoutDirection.Rtl else LayoutDirection.Ltr){DnsProtectionScreen(arabic,null,{back=true})}}}}
            compose.onNodeWithText(if(arabic)"اتصل بالراوتر أولًا لإعداد الحماية." else "Connect to a router to configure protection.").assertExists()
            compose.onNodeWithText(if(arabic)"معاينة التغييرات" else "Preview changes").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithText(if(arabic)"تحديث الفحص" else "Refresh inspection").assertIsNotEnabled()
            compose.onNodeWithText(if(arabic)"رجوع" else "Back").performScrollTo().assertIsDisplayed()
            capture(if(arabic)"dns-protection-ar.png" else "dns-protection-en.png")
            compose.onNodeWithText(if(arabic)"رجوع" else "Back").performClick();assertTrue(back);back=false
        }
    }
}
