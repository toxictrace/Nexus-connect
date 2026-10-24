package com.toxictrace.nexusconnect.widget

import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import com.toxictrace.nexusconnect.util.AppLogger

/**
 * Watches contacts, contact data (including photo rows), and call log.
 * A short debounce coalesces provider notifications before rebuilding widgets.
 */
class ContactsObserverService : Service() {

    private var contactsObserver: ContentObserver? = null
    private var contactDataObserver: ContentObserver? = null
    private var callLogObserver: ContentObserver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingUpdate: Runnable? = null
    private var pendingSource: String = "unknown"

    private fun scheduleWidgetUpdate(source: String) {
        pendingSource = source
        pendingUpdate?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            AppLogger.i("ContactsObserverService", "widget update triggered by: $pendingSource")
            PhotoProvider.invalidateCache()
            ContactWidgetProvider.updateAllWidgets(applicationContext)
            pendingUpdate = null
        }
        pendingUpdate = r
        handler.postDelayed(r, 2000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (contactsObserver == null) {
            contactsObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    scheduleWidgetUpdate("contacts")
                }
            }
            contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, contactsObserver!!
            )
        }

        // Some Contacts providers notify the Data table for photo changes without
        // delivering a useful notification on the aggregate Contacts URI.
        if (contactDataObserver == null) {
            contactDataObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    scheduleWidgetUpdate("contact-data")
                }
            }
            contentResolver.registerContentObserver(
                ContactsContract.Data.CONTENT_URI, true, contactDataObserver!!
            )
        }

        if (callLogObserver == null) {
            callLogObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    scheduleWidgetUpdate("calllog")
                }
            }
            contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI, true, callLogObserver!!
            )
        }

        return START_STICKY
    }

    override fun onDestroy() {
        pendingUpdate?.let { handler.removeCallbacks(it) }
        contactsObserver?.let { contentResolver.unregisterContentObserver(it) }
        contactDataObserver?.let { contentResolver.unregisterContentObserver(it) }
        callLogObserver?.let { contentResolver.unregisterContentObserver(it) }
        contactsObserver = null
        contactDataObserver = null
        callLogObserver = null
        pendingUpdate = null
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
