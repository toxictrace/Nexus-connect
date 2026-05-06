package com.toxictrace.nexusconnect.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import com.toxictrace.nexusconnect.util.AppLogger

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
        AppLogger.i("CallStateReceiver", "phone state: $state")

        // Update widget when call ends (IDLE = hung up)
        // Delay 2s to allow call log to be written before widget reads it
        if (state == TelephonyManager.EXTRA_STATE_IDLE) {
            AppLogger.i("CallStateReceiver", "call ended, scheduling widget update in 2s")
            Handler(Looper.getMainLooper()).postDelayed({
                AppLogger.i("CallStateReceiver", "updating widget after call")
                PhotoProvider.invalidateCache()
                ContactWidgetProvider.updateAllWidgets(context)
            }, 2000)
        }
    }
}
