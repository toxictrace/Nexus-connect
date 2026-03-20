package com.toxictrace.nexusconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.toxictrace.nexusconnect.ui.NexusApp
import com.toxictrace.nexusconnect.ui.theme.NexusConnectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexusConnectTheme {
                NexusApp()
            }
        }
    }
}
