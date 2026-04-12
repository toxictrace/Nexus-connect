package com.toxictrace.nexusconnect

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.toxictrace.nexusconnect.ui.NexusApp
import com.toxictrace.nexusconnect.util.AppLogger
import com.toxictrace.nexusconnect.util.LocaleHelper

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getSavedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.applyLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(applicationContext)
        AppLogger.i("MainActivity", "App started. versionName=${BuildConfig.VERSION_NAME}")
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            NexusApp()
        }
    }

    override fun onResume() {
        super.onResume()
        AppLogger.i("MainActivity", "onResume")
    }

    override fun onPause() {
        super.onPause()
        AppLogger.i("MainActivity", "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i("MainActivity", "onDestroy")
    }
}
