package com.toxictrace.nexusconnect.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Listens for phone state changes (call ended) and updates the widget.
 * Works even when the app is killed — system delivers broadcasts to receivers
 * declared in AndroidManifest.
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.d("CallStateReceiver", "phone state: $state")

        // Update widget when call ends (IDLE = hung up)
        if (state == TelephonyManager.EXTRA_STATE_IDLE) {
            ContactWidgetProvider.updateAllWidgets(context)
        }
    }
}
