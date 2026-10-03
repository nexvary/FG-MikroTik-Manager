package com.fgmachines.mikrotikmanager.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fgmachines.mikrotikmanager.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationBackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun start(section: AppSection) {
        compose.runOnUiThread {
            compose.activity.setContent {
                var state by remember { mutableStateOf(RouterUiState(connected = true, section = section)) }
                FgMikroTikTheme {
                    RouterShell(state = state, arabic = false,
                        onLanguageToggle = {}, onRefresh = {}, onDisconnect = {},
                        onSection = { state = state.copy(section = it, adminModule = null) },
                        onOpenAdminModule = { state = state.copy(adminModule = it) },
                        onRefreshAdminModule = {}, onCloseAdminModule = { state = state.copy(adminModule = null) },
                        onVoucherModeSelected = {}, onVoucherBatchGenerated = {}, onProvisionVouchers = {},
                        onClearVoucherResult = {}, onRunCommands = {}, onClearCommandResults = {},
                        onCreateAdminItem = {}, onCreateRouterAdmin = { _, _, _, _ -> },
                        onUpdateAdminItem = { _, _ -> }, onToggleAdminItem = { _, _ -> },
                        onRemoveAdminItem = {}, onClearAdminActionMessage = {})
                }
            }
        }
        compose.waitForIdle()
    }
    private fun tap(text: String) {
        val node = compose.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }
    private fun phoneBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    @Test fun terminalCloseKeepsNetworkAndThenReturnsOneLevel() {
        start(AppSection.NETWORK)
        tap("Terminal / Command Center")
        tap("Back to previous page")
        compose.onNodeWithText("Terminal / Command Center").assertExists()
        tap("Terminal / Command Center")
        // The actual device Back key must dismiss the modal window, not its parent.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(5000) { compose.onAllNodesWithText("Back to previous page").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Terminal / Command Center").assertExists()
    }
    @Test fun headerAndPhoneBackRestoreAdvancedSubpage() {
        start(AppSection.ADVANCED)
        tap("Technical settings")
        tap("Open Network management")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Open Network management").assertExists()
        tap("Open Network management")
        phoneBack()
        compose.onNodeWithText("Open Network management").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Technical settings").assertExists()
    }
}
