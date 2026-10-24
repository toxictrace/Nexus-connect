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
 * Photo providers may publish the row change before the aggregate contact photo is ready,
 * so a short series of refreshes gives the provider time to finish updating its photo URI.
 */
class ContactsObserverService : Service() {

    private var contactsObserver: ContentObserver? = null
    private var contactDataObserver: ContentObserver? = null
    private var callLogObserver: ContentObserver? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pendingUpdates = mutableListOf<Runnable>()

    private fun scheduleWidgetUpdate(source: String) {
        pendingUpdates.forEach(handler::removeCallbacks)
        pendingUpdates.clear()

        // Refresh once promptly, then retry after the Contacts provider has had
        // more time to finish saving/rebuilding the aggregate photo.
        listOf(1500L, 4000L, 8000L).forEach { delay ->
            val task = Runnable {
                AppLogger.i(
                    "ContactsObserverService",
                    "widget refresh from $source after ${delay}ms"
                )
                PhotoProvider.invalidateCache()
                ContactWidgetProvider.updateAllWidgets(applicationContext)
                pendingUpdates.removeAll { it === this }
            }
            pendingUpdates.add(task)
            handler.postDelayed(task, delay)
        }
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
        pendingUpdates.forEach(handler::removeCallbacks)
        pendingUpdates.clear()
        contactsObserver?.let { contentResolver.unregisterContentObserver(it) }
        contactDataObserver?.let { contentResolver.unregisterContentObserver(it) }
        callLogObserver?.let { contentResolver.unregisterContentObserver(it) }
        contactsObserver = null
        contactDataObserver = null
        callLogObserver = null
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
