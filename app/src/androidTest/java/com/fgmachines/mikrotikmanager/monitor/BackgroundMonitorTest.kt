package com.fgmachines.mikrotikmanager.monitor

import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fgmachines.mikrotikmanager.MainActivity
import com.fgmachines.mikrotikmanager.data.RouterProfile
import com.fgmachines.mikrotikmanager.data.RouterProtocol
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundMonitorTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realServiceKeepsMonitoringAfterHomeAndClearsOnStop() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        if(Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        try {
            compose.runOnUiThread{
                repeat(4){i->BackgroundMonitor.start(context,RouterProfile("background-test-$i","Test router $i","","127.0.0.1",1,"admin",RouterProtocol.API_SSL),"temporary-test-password",false)}
                assertTrue(runCatching{BackgroundMonitor.start(context,RouterProfile("fifth","Fifth router","","127.0.0.1",1,"admin",RouterProtocol.API_SSL),"temporary-test-password",false)}.isFailure)
            }
            compose.waitUntil(8000){BackgroundMonitor.routers.value.size==4}
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any{it.id==700})
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            instrumentation.waitForIdleSync()
            Thread.sleep(1000) // Give the activity ON_STOP a chance to run before asserting service ownership.
            // Closing the activity/screen must not tear down the separate service scope.
            compose.waitUntil(36000){BackgroundMonitor.alerts.value.count{it.kind=="UNREACHABLE"}==4}
            assertEquals(4,BackgroundMonitor.routers.value.size)
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any{it.id!=700})
            BackgroundMonitor.stop(context,"background-test-0")
            compose.waitUntil(8000){BackgroundMonitor.routers.value.size==3}
            assertFalse(BackgroundMonitor.routers.value.containsKey("background-test-0"))
            assertTrue(BackgroundMonitor.pending.isEmpty())
            BackgroundMonitor.stopAll(context)
            compose.waitUntil(8000){BackgroundMonitor.routers.value.isEmpty()}
            assertTrue(BackgroundMonitor.pending.isEmpty())
        }finally{BackgroundMonitor.stopAll(context)}
    }
}
