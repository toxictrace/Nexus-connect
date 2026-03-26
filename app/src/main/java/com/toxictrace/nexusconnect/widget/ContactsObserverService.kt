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

/**
 * Lightweight service that registers ContentObservers on contacts
 * and call log — updates widget when either changes.
 */
class ContactsObserverService : Service() {

    private var contactsObserver: ContentObserver? = null
    private var callLogObserver: ContentObserver? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val handler = Handler(Looper.getMainLooper())

        if (contactsObserver == null) {
            contactsObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    PhotoProvider.invalidateCache() // force launcher to reload photos
                    ContactWidgetProvider.updateAllWidgets(applicationContext)
                }
            }
            contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, contactsObserver!!
            )
        }

        if (callLogObserver == null) {
            callLogObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    ContactWidgetProvider.updateAllWidgets(applicationContext)
                }
            }
            contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI, true, callLogObserver!!
            )
        }

        return START_STICKY
    }

    override fun onDestroy() {
        contactsObserver?.let { contentResolver.unregisterContentObserver(it) }
        callLogObserver?.let { contentResolver.unregisterContentObserver(it) }
        contactsObserver = null
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
