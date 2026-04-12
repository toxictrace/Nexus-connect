package com.toxictrace.nexusconnect.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootAndUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d("NexusWidget", "BootAndUpdateReceiver: ${intent.action}")
                ContactsObserverService.start(context)
                ContactWidgetProvider.updateAllWidgets(context)
            }
        }
    }
}
