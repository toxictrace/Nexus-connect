package com.toxictrace.nexusconnect.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import com.toxictrace.nexusconnect.R
import com.toxictrace.nexusconnect.data.model.Contact
import com.toxictrace.nexusconnect.data.model.PriorityApp
import com.toxictrace.nexusconnect.data.preferences.SettingsRepository
import com.toxictrace.nexusconnect.data.repository.ContactsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ContactWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "NexusWidget"
        const val ACTION_CONTACT_CLICK = "com.toxictrace.nexusconnect.ACTION_CONTACT_CLICK"
        const val EXTRA_CONTACT_ID    = "extra_contact_id"
        const val EXTRA_CONTACT_PHONE = "extra_contact_phone"
        const val EXTRA_CONTACT_NAME  = "extra_contact_name"

        fun updateAllWidgets(context: Context) {
            Log.d(TAG, "updateAllWidgets called")
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ContactWidgetProvider::class.java))
            Log.d(TAG, "Widget IDs: ${ids.toList()}")
            ids.forEach { id -> buildAndPushWidget(context, mgr, id) }
        }

        fun buildAndPushWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            Log.d(TAG, "buildAndPushWidget id=$widgetId")

            val settingsRepo  = SettingsRepository(context)
            val contactsRepo  = ContactsRepository(context)

            val settings = runBlocking { settingsRepo.settings.first() }
            val allContacts = contactsRepo.loadContacts()
            val selectedIds = runBlocking { settingsRepo.getSelectedContactIds() }

            Log.d(TAG, "allContacts=${allContacts.size}, selectedIds=${selectedIds.size}, settings.columns=${settings.columns}")

            val contacts: List<Contact> = when {
                selectedIds.isNotEmpty() ->
                    selectedIds.mapNotNull { id -> allContacts.firstOrNull { it.id == id } }
                        .take(settings.maxContacts)
                settings.filterFavorites ->
                    allContacts.filter { it.isStarred }.take(settings.maxContacts)
                else ->
                    allContacts.take(settings.maxContacts)
            }

            Log.d(TAG, "contacts to show: ${contacts.size}")

            val cols = settings.columns
            val rows = if (contacts.isEmpty()) 1
                       else (contacts.size + cols - 1) / cols

            // Root vertical LinearLayout
            val root = RemoteViews(context.packageName, R.layout.widget_root)

            for (rowIdx in 0 until rows) {
                val rowViews = RemoteViews(context.packageName, R.layout.widget_row)

                for (colIdx in 0 until cols) {
                    val pos = rowIdx * cols + colIdx
                    val contact = contacts.getOrNull(pos)

                    if (contact == null) {
                        // Empty cell
                        rowViews.addView(
                            R.id.widget_row_container,
                            RemoteViews(context.packageName, R.layout.widget_empty_tile)
                        )
                    } else {
                        val tileViews = RemoteViews(context.packageName, R.layout.widget_tile)

                        // Name
                        tileViews.setTextViewText(R.id.tile_name,
                            contact.name.split(" ").firstOrNull() ?: contact.name)

                        // Photo or initials
                        val bmp = loadPhoto(context, contact, 120)
                            ?: makeInitials(contact, 120)
                        tileViews.setImageViewBitmap(R.id.tile_photo, bmp)

                        // Click PendingIntent (unique per contact)
                        val clickIntent = Intent(context, ContactWidgetProvider::class.java).apply {
                            action = ACTION_CONTACT_CLICK
                            putExtra(EXTRA_CONTACT_ID,    contact.id)
                            putExtra(EXTRA_CONTACT_PHONE, contact.phoneNumber ?: "")
                            putExtra(EXTRA_CONTACT_NAME,  contact.name)
                            // Make unique so Android doesn't reuse wrong PendingIntent
                            data = Uri.parse("nexus://contact/${contact.id}")
                        }
                        val pi = PendingIntent.getBroadcast(
                            context,
                            contact.id.toInt(),
                            clickIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        tileViews.setOnClickPendingIntent(R.id.tile_photo, pi)
                        tileViews.setOnClickPendingIntent(R.id.tile_name, pi)

                        rowViews.addView(R.id.widget_row_container, tileViews)
                    }
                }

                root.addView(R.id.widget_container, rowViews)
            }

            mgr.updateAppWidget(widgetId, root)
            Log.d(TAG, "Widget updated successfully")
        }

        private fun loadPhoto(context: Context, contact: Contact, maxPx: Int): Bitmap? {
            val uri = contact.photoUri ?: return null
            return try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { s ->
                    BitmapFactory.decodeStream(s, null, boundsOpts)
                }
                val sample = maxOf(1, maxOf(boundsOpts.outWidth, boundsOpts.outHeight) / maxPx)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { s ->
                    BitmapFactory.decodeStream(s, null, decodeOpts)
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadPhoto failed for ${contact.name}: ${e.message}")
                null
            }
        }

        private fun makeInitials(contact: Contact, size: Int): Bitmap {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val colors = intArrayOf(
                0xFF1A3CA8.toInt(), 0xFF7B3FA0.toInt(), 0xFF007A6E.toInt(),
                0xFF8B2252.toInt(), 0xFF2E7D32.toInt(), 0xFFB85C00.toInt()
            )
            paint.color = colors[(contact.id % colors.size).toInt()]
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            val initials = contact.name.split(" ")
                .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
            paint.color = Color.WHITE
            paint.textSize = size * 0.38f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(initials, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
            return bmp
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Log.d(TAG, "onUpdate ids=${ids.toList()}")
        ids.forEach { buildAndPushWidget(context, mgr, it) }
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
            Log.d(TAG, "Contact clicked: $name id=$id phone=$phone")
            ContactActionHandler.handle(context, id, phone, name)
        }
    }
}
