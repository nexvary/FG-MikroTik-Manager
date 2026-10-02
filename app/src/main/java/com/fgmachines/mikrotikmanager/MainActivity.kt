package com.fgmachines.mikrotikmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fgmachines.mikrotikmanager.ui.RouterApp
import com.fgmachines.mikrotikmanager.ui.RouterDemoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val demo = BuildConfig.DEBUG &&
            intent.getBooleanExtra("ui_demo", false)
        val demoScreen = intent.getStringExtra("demo_screen").orEmpty()

        setContent {
            if (demo) {
                RouterDemoApp(demoScreen)
            } else {
                RouterApp()
            }
        }
    }
}
