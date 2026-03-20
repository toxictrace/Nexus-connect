package com.toxictrace.nexusconnect.widget

import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract

/**
 * Lightweight foreground-less service that registers a ContentObserver
 * on the contacts database and calls ContactWidgetProvider.updateAllWidgets()
 * when something changes.
 */
class ContactsObserverService : Service() {

    private var observer: ContentObserver? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (observer == null) {
            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    ContactWidgetProvider.updateAllWidgets(applicationContext)
                }
            }
            contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, observer!!
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
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
