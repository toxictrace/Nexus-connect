package com.toxictrace.nexusconnect.widget

import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import com.toxictrace.nexusconnect.util.AppLogger

/**
 * Observes the call log while active. Contact changes are handled by
 * ContactsChangeJobService so refreshes still work when this service stops.
 */
class ContactsObserverService : Service() {

    private var callLogObserver: ContentObserver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdate: Runnable? = null

    private fun scheduleWidgetUpdate(source: String) {
        val hadPending = pendingUpdate != null
        pendingUpdate?.let { handler.removeCallbacks(it) }
        AppLogger.i("ContactsObserverService", "scheduleWidgetUpdate: source=$source replacingPending=$hadPending debounceMs=2000")
        val r = Runnable {
            AppLogger.i("ContactsObserverService", "widget update triggered by: $source")
            PhotoProvider.invalidateCache()
            ContactWidgetProvider.updateAllWidgets(applicationContext)
        }
        pendingUpdate = r
        handler.postDelayed(r, 2000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i("ContactsObserverService", "onStartCommand: startId=$startId")
        if (callLogObserver == null) {
            callLogObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    AppLogger.i("ContactsObserverService", "calllog onChange(selfChange=$selfChange, uri=<not supplied>)")
                    scheduleWidgetUpdate("calllog")
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    AppLogger.i("ContactsObserverService", "calllog onChange(selfChange=$selfChange, uri=$uri)")
                    scheduleWidgetUpdate("calllog uri=$uri")
                }
            }
            contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI, true, callLogObserver!!
            )
            AppLogger.i("ContactsObserverService", "registered calllog observer uri=${CallLog.Calls.CONTENT_URI} descendants=true")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pendingUpdate?.let { handler.removeCallbacks(it) }
        callLogObserver?.let { contentResolver.unregisterContentObserver(it) }
        callLogObserver = null
        AppLogger.i("ContactsObserverService", "onDestroy: call log observer unregistered")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, ContactsObserverService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, ContactsObserverService::class.java))
        }
    }
}
