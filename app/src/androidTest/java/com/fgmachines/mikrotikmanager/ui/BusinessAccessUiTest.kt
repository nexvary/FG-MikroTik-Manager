package com.fgmachines.mikrotikmanager.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import org.junit.Assert.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fgmachines.mikrotikmanager.MainActivity
import com.fgmachines.mikrotikmanager.business.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

class AccessWorkspaceProbe(app:Application,private val saved:SavedStateHandle):androidx.lifecycle.AndroidViewModel(app) {
    var draft by mutableStateOf(saved.get<String>("draft") ?: "");private set
    var closed=false;private set
    fun edit(text:String){draft=text;saved["draft"]=text}
    override fun onCleared(){closed=true;super.onCleared()}
}

@RunWith(AndroidJUnit4::class)
class BusinessAccessUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun waitText(text:String){compose.waitUntil(15000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun tap(text:String){waitText(text);compose.waitUntil(15000){runCatching{compose.onNodeWithText(text).assertIsEnabled();true}.getOrDefault(false)};val node=compose.onNodeWithText(text);runCatching{node.performScrollTo()};node.performClick()}
    private fun capture(name:String){compose.waitForIdle();val i=InstrumentationRegistry.getInstrumentation();val bitmap=i.uiAutomation.takeScreenshot();i.targetContext.openFileOutput(name,0).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    @Test fun ownerCreatesLoginAndReadOnlyUserCannotEnterRouterWorkspace() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="access-ui-${UUID.randomUUID()}.db";val store=BusinessStore(BusinessDatabase(context,name));val identity=BusinessIdentity(store)
        var model:BusinessAccessModel?=null
        var workspaceProbe:AccessWorkspaceProbe?=null
        try {
            val scope=store.defaultScope();BusinessTeam(store).add(scope,"reader","UI staff","","READ_ONLY","EGP",0)
            identity.bootstrap("Owner password 123".toCharArray());identity.lock()
            compose.runOnUiThread {
                model=androidx.lifecycle.ViewModelProvider(compose.activity,object:androidx.lifecycle.ViewModelProvider.Factory{
                    @Suppress("UNCHECKED_CAST")override fun <T:androidx.lifecycle.ViewModel> create(clazz:Class<T>):T=BusinessAccessModel(context.applicationContext as Application,SavedStateHandle(),name) as T
                }).get("accessFixture",BusinessAccessModel::class.java)
                compose.activity.setContent {
                    BusinessAccessRoot(model!!){val manage=LocalManageAccess.current;val probe:AccessWorkspaceProbe=androidx.lifecycle.viewmodel.compose.viewModel();workspaceProbe=probe;androidx.compose.foundation.layout.Column{Text("Router workspace fixture");OutlinedTextField(probe.draft,probe::edit,label={Text("Owner draft")});Button(onClick=manage){Text("Manage access")}}}
                }
            }
            waitText("Employee sign in");tap("العربية");waitText("دخول الموظفين");capture("access-login-ar.png");tap("English")
            compose.onNodeWithText("Login name").performTextInput("owner");compose.onNodeWithText("Password").performTextInput("Owner password 123");tap("Sign in")
            waitText("Router workspace fixture");val ownerProbe=workspaceProbe!!;compose.onNodeWithText("Owner draft").performTextInput("Private owner draft");tap("Manage access");waitText("UI staff • READ_ONLY");tap("UI staff • READ_ONLY")
            compose.onNodeWithText("Login name (Latin letters/digits)").performTextInput("ui.reader")
            compose.onNodeWithText("New password").performTextInput("Reader password 456")
            compose.onNodeWithText("Confirm password").performTextInput("Reader password 456");tap("Save")
            waitText("ui.reader • READ_ONLY");capture("access-accounts-en.png");tap("Back");waitText("Router workspace fixture");compose.onNodeWithText("Private owner draft").assertExists();assertSame(ownerProbe,workspaceProbe);tap("Lock")
            waitText("Employee sign in");assertTrue(ownerProbe.closed);compose.onNodeWithText("Login name").performTextInput("ui.reader");compose.onNodeWithText("Password").performTextInput("Reader password 456");tap("Sign in")
            waitText("Employee workspace");compose.onNodeWithText("Router workspace fixture").assertDoesNotExist();capture("access-read-only-en.png")
            tap("Lock");waitText("Employee sign in")
            compose.onNodeWithText("Reader password 456").assertDoesNotExist()
            compose.onNodeWithText("Login name").performTextClearance();compose.onNodeWithText("Login name").performTextInput("owner");compose.onNodeWithText("Password").performTextInput("Owner password 123");tap("Sign in")
            waitText("Router workspace fixture");assertNotSame(ownerProbe,workspaceProbe);assertEquals("",workspaceProbe!!.draft);compose.onNodeWithText("Private owner draft").assertDoesNotExist()
        } finally {
            compose.runOnUiThread{compose.activity.setContent{Text("Access test complete")};compose.activity.viewModelStore.clear()}
            identity.lock();store.close();context.deleteDatabase(name)
        }
    }
}
