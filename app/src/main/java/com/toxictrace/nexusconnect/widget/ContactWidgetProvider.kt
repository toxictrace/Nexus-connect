package com.toxictrace.nexusconnect.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.toxictrace.nexusconnect.R
import com.toxictrace.nexusconnect.data.preferences.WidgetPrefs
import com.toxictrace.nexusconnect.data.repository.ContactsRepository

class ContactWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "NexusWidget"
        const val ACTION_CONTACT_CLICK = "com.toxictrace.nexusconnect.ACTION_CONTACT_CLICK"
        const val EXTRA_CONTACT_ID    = "extra_contact_id"
        const val EXTRA_CONTACT_PHONE = "extra_contact_phone"
        const val EXTRA_CONTACT_NAME  = "extra_contact_name"

        private val TILE_IDS = intArrayOf(
            R.id.tile_0,  R.id.tile_1,  R.id.tile_2,  R.id.tile_3,
            R.id.tile_4,  R.id.tile_5,  R.id.tile_6,  R.id.tile_7,
            R.id.tile_8,  R.id.tile_9,  R.id.tile_10, R.id.tile_11
        )
        private val NAME_IDS = intArrayOf(
            R.id.name_0,  R.id.name_1,  R.id.name_2,  R.id.name_3,
            R.id.name_4,  R.id.name_5,  R.id.name_6,  R.id.name_7,
            R.id.name_8,  R.id.name_9,  R.id.name_10, R.id.name_11
        )

        fun updateAllWidgets(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, ContactWidgetProvider::class.java)
            )
            Log.d(TAG, "updateAllWidgets: ${ids.size} widgets")
            ids.forEach { buildAndPush(context, mgr, it) }
        }

        fun buildAndPush(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            Log.d(TAG, "buildAndPush id=$widgetId")

            val maxContacts     = WidgetPrefs.getMaxContacts(context)
            val filterFavorites = WidgetPrefs.getFilterFavorites(context)
            val selectedIds     = WidgetPrefs.getSelectedContactIds(context)

            Log.d(TAG, "selectedIds=${selectedIds.size} maxContacts=$maxContacts")

            val allContacts = try {
                ContactsRepository(context).loadContacts()
            } catch (e: Exception) {
                Log.e(TAG, "loadContacts failed: ${e.message}")
                emptyList()
            }
            Log.d(TAG, "allContacts=${allContacts.size}")

            val contacts = when {
                selectedIds.isNotEmpty() ->
                    selectedIds.mapNotNull { id -> allContacts.firstOrNull { it.id == id } }
                        .take(minOf(maxContacts, 12))
                filterFavorites ->
                    allContacts.filter { it.isStarred }.take(minOf(maxContacts, 12))
                else ->
                    allContacts.take(minOf(maxContacts, 12))
            }
            Log.d(TAG, "contacts to show: ${contacts.size}")

            val views = RemoteViews(context.packageName, R.layout.widget_grid_4x3)

            for (idx in 0..11) {
                val contact = contacts.getOrNull(idx)
                if (contact != null) {
                    views.setViewVisibility(TILE_IDS[idx], View.VISIBLE)
                    views.setTextViewText(NAME_IDS[idx],
                        contact.name.split(" ").firstOrNull() ?: contact.name)

                    val intent = Intent(context, ContactWidgetProvider::class.java).apply {
                        action = ACTION_CONTACT_CLICK
                        putExtra(EXTRA_CONTACT_ID,    contact.id)
                        putExtra(EXTRA_CONTACT_PHONE, contact.phoneNumber ?: "")
                        putExtra(EXTRA_CONTACT_NAME,  contact.name)
                        data = Uri.parse("nexus://contact/${contact.id}")
                    }
                    val pi = PendingIntent.getBroadcast(
                        context, contact.id.toInt(), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(TILE_IDS[idx], pi)
                } else {
                    views.setViewVisibility(TILE_IDS[idx], View.INVISIBLE)
                }
            }

            try {
                mgr.updateAppWidget(widgetId, views)
                Log.d(TAG, "buildAndPush done")
            } catch (e: Exception) {
                Log.e(TAG, "updateAppWidget FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Log.d(TAG, "onUpdate ${ids.toList()}")
        ids.forEach { buildAndPush(context, mgr, it) }
    }
    override fun onEnabled(context: Context) {
        Log.d(TAG, "onEnabled")
        ContactsObserverService.start(context)
    }
    override fun onDisabled(context: Context) {
        Log.d(TAG, "onDisabled")
        ContactsObserverService.stop(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive action=${intent.action}")
        if (intent.action == ACTION_CONTACT_CLICK) {
            val id    = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
            val phone = intent.getStringExtra(EXTRA_CONTACT_PHONE)
            val name  = intent.getStringExtra(EXTRA_CONTACT_NAME)
            Log.d(TAG, "Click: $name id=$id")
            ContactActionHandler.handle(context, id, phone, name)
        }
    }
}
