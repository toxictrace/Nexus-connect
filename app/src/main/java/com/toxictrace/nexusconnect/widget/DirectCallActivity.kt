package com.toxictrace.nexusconnect.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import com.toxictrace.nexusconnect.data.preferences.WidgetPrefs

class DirectCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phone = intent.getStringExtra("phone") ?: ""
        if (phone.isBlank()) { finish(); return }

        // Vibrate using window decor view with IGNORE flags
        if (WidgetPrefs.getHapticFeedback(this)) {
            try {
                val v = window.decorView
                v.isHapticFeedbackEnabled = true
                v.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            } catch (_: Exception) {}
        }

        val hasPermission = checkSelfPermission(Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        startActivity(Intent(action, Uri.parse("tel:$phone")))
        finish()
    }
}
