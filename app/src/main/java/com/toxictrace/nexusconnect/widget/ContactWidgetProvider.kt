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
import android.view.View
import android.widget.RemoteViews
import com.toxictrace.nexusconnect.data.model.Contact
import com.toxictrace.nexusconnect.data.preferences.WidgetPrefs
import com.toxictrace.nexusconnect.data.repository.ContactsRepository

class ContactWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "NexusWidget"
        const val ACTION_CONTACT_CLICK = "com.toxictrace.nexusconnect.ACTION_CONTACT_CLICK"
        const val EXTRA_CONTACT_ID    = "extra_contact_id"
        const val EXTRA_CONTACT_PHONE = "extra_contact_phone"
        const val EXTRA_CONTACT_NAME  = "extra_contact_name"

        private const val BITMAP_SIZE = 120

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

            val cols            = WidgetPrefs.getColumns(context).coerceIn(3, 6)
            val rows            = WidgetPrefs.getRows(context).coerceIn(2, 4)
            val maxContacts     = WidgetPrefs.getMaxContacts(context)
            val filterFavorites = WidgetPrefs.getFilterFavorites(context)
            val selectedIds     = WidgetPrefs.getSelectedContactIds(context)

            val maxTiles = cols * rows

            val allContacts = try {
                ContactsRepository(context).loadContacts()
            } catch (e: Exception) {
                Log.e(TAG, "loadContacts: ${e.message}")
                emptyList()
            }

            val contacts = when {
                selectedIds.isNotEmpty() ->
                    selectedIds.mapNotNull { id -> allContacts.firstOrNull { it.id == id } }
                        .take(minOf(maxContacts, maxTiles))
                filterFavorites ->
                    allContacts.filter { it.isStarred }.take(minOf(maxContacts, maxTiles))
                else ->
                    allContacts.take(minOf(maxContacts, maxTiles))
            }
            Log.d(TAG, "cols=$cols rows=$rows contacts=${contacts.size}")

            val layoutRes = context.resources.getIdentifier(
                "widget_grid_${cols}c${rows}r", "layout", context.packageName
            ).takeIf { it != 0 } ?: run {
                Log.e(TAG, "Layout not found for ${cols}c${rows}r, using 4c3r")
                context.resources.getIdentifier("widget_grid_4c3r", "layout", context.packageName)
            }
            val views = RemoteViews(context.packageName, layoutRes)
            val pkg = context.packageName

            for (idx in 0 until maxTiles) {
                val tileId  = context.resources.getIdentifier("tile_${cols}r${rows}_$idx",  "id", pkg)
                val photoId = context.resources.getIdentifier("photo_${cols}r${rows}_$idx", "id", pkg)
                val nameId  = context.resources.getIdentifier("name_${cols}r${rows}_$idx",  "id", pkg)

                val contact = contacts.getOrNull(idx)
                if (contact != null) {
                    views.setViewVisibility(tileId, View.VISIBLE)
                    val bmp = loadPhoto(context, contact, BITMAP_SIZE)
                        ?: makeInitials(contact, BITMAP_SIZE)
                    views.setImageViewBitmap(photoId, bmp)
                    views.setTextViewText(nameId,
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
                    views.setOnClickPendingIntent(tileId, pi)
                } else {
                    views.setViewVisibility(tileId, View.INVISIBLE)
                }
            }

            try {
                mgr.updateAppWidget(widgetId, views)
                Log.d(TAG, "buildAndPush done")
            } catch (e: Exception) {
                Log.e(TAG, "FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        private fun loadPhoto(context: Context, contact: Contact, maxPx: Int): Bitmap? {
            val uri = contact.photoUri ?: return null
            return try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { s ->
                    BitmapFactory.decodeStream(s, null, boundsOpts)
                }
                val sample = maxOf(1, maxOf(boundsOpts.outWidth, boundsOpts.outHeight) / maxPx)
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { s ->
                    BitmapFactory.decodeStream(s, null, opts)
                }
            } catch (e: Exception) { null }
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
            canvas.drawText(initials, size / 2f,
                size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
            return bmp
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
