package com.toxictrace.nexusconnect.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.toxictrace.nexusconnect.data.preferences.WidgetPrefs
import com.toxictrace.nexusconnect.util.AppLogger

class WidgetActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WidgetActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ContactWidgetProvider.ACTION_OPEN_CALL_LOG -> openCallLog(context)
            ContactWidgetProvider.ACTION_CONTACT_CLICK -> handleContactClick(context, intent)
        }
    }

    private fun openCallLog(context: Context) {
        AppLogger.i(TAG, "open call log")
        val preferredPkg = WidgetPrefs.getCallLogAppPackage(context)
        if (preferredPkg.isNotBlank()) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(preferredPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                AppLogger.i(TAG, "open call log: launching preferred package $preferredPkg directly")
                context.startActivity(launchIntent)
                return
            } else {
                AppLogger.w(TAG, "open call log: preferred package $preferredPkg not launchable, falling back")
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "vnd.android.cursor.dir/calls"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun handleContactClick(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ContactWidgetProvider.EXTRA_CONTACT_ID, -1L)
        val phone = intent.getStringExtra(ContactWidgetProvider.EXTRA_CONTACT_PHONE)
        val name = intent.getStringExtra(ContactWidgetProvider.EXTRA_CONTACT_NAME)
        Log.d(TAG, "Click: $name id=$id")
        ContactActionHandler.handle(context, id, phone, name)
    }
}
