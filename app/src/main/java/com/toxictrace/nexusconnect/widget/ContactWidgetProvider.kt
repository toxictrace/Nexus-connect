package com.toxictrace.nexusconnect.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.toxictrace.nexusconnect.R

class ContactWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Register contacts observer when first widget is placed
        ContactsObserverService.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        ContactsObserverService.stop(context)
    }

    companion object {
        const val ACTION_CONTACT_CLICK = "com.toxictrace.nexusconnect.ACTION_CONTACT_CLICK"
        const val EXTRA_CONTACT_ID     = "extra_contact_id"
        const val EXTRA_CONTACT_PHONE  = "extra_contact_phone"
        const val EXTRA_CONTACT_NAME   = "extra_contact_name"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ContactWidgetProvider::class.java)
            )
            ids.forEach { id ->
                // Notify the list adapter data changed
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_grid)
                updateWidget(context, manager, id)
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val serviceIntent = Intent(context, ContactWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Make the intent unique per widget so extras are not cached away
                data = android.net.Uri.fromParts("widget", appWidgetId.toString(), null)
            }

            val views = RemoteViews(context.packageName, R.layout.widget_contact_grid).apply {
                setRemoteAdapter(R.id.widget_grid, serviceIntent)
                setEmptyView(R.id.widget_grid, android.R.id.empty)

                // Template PendingIntent — filled per-item by the factory
                val clickIntent = Intent(context, ContactWidgetProvider::class.java).apply {
                    action = ACTION_CONTACT_CLICK
                }
                val clickPi = PendingIntent.getBroadcast(
                    context, 0, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                setPendingIntentTemplate(R.id.widget_grid, clickPi)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /** Handle tile click broadcast */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CONTACT_CLICK) {
            val contactId   = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
            val phone       = intent.getStringExtra(EXTRA_CONTACT_PHONE)
            val name        = intent.getStringExtra(EXTRA_CONTACT_NAME)
            ContactActionHandler.handle(context, contactId, phone, name)
        }
    }
}
