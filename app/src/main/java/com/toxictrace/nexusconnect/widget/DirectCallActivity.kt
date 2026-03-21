package com.toxictrace.nexusconnect.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.toxictrace.nexusconnect.data.preferences.WidgetPrefs

/**
 * Transparent Activity that vibrates then immediately starts a phone call.
 * Needed because BroadcastReceiver context can't reliably vibrate.
 */
class DirectCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phone = intent.getStringExtra("phone") ?: ""
        if (phone.isBlank()) { finish(); return }

        if (WidgetPrefs.getHapticFeedback(this)) HapticHelper.vibrate(this)

        val hasPermission = checkSelfPermission(Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        startActivity(Intent(action, Uri.parse("tel:$phone")))
        finish()
    }
}
