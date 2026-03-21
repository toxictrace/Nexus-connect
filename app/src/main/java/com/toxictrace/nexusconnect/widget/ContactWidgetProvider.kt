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
import com.toxictrace.nexusconnect.R
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

        // IDs per column count — must match generated XML layouts
        private val TILE_IDS = mapOf(
            3 to Array(9)  { i -> resId("tile_3_$i") },
            4 to Array(12) { i -> resId("tile_4_$i") },
            5 to Array(15) { i -> resId("tile_5_$i") },
            6 to Array(18) { i -> resId("tile_6_$i") }
        )
        private val PHOTO_IDS = mapOf(
            3 to Array(9)  { i -> resId("photo_3_$i") },
            4 to Array(12) { i -> resId("photo_4_$i") },
            5 to Array(15) { i -> resId("photo_5_$i") },
            6 to Array(18) { i -> resId("photo_6_$i") }
        )
        private val NAME_IDS = mapOf(
            3 to Array(9)  { i -> resId("name_3_$i") },
            4 to Array(12) { i -> resId("name_4_$i") },
            5 to Array(15) { i -> resId("name_5_$i") },
            6 to Array(18) { i -> resId("name_6_$i") }
        )
        private val LAYOUTS = mapOf(
            3 to R.layout.widget_grid_3col,
            4 to R.layout.widget_grid_4col,
            5 to R.layout.widget_grid_5col,
            6 to R.layout.widget_grid_6col
        )

        private fun resId(name: String): Int {
            // Will be resolved at runtime via R.id reflection-free lookup
            return 0 // placeholder — see buildAndPush
        }

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
            val maxContacts     = WidgetPrefs.getMaxContacts(context)
            val filterFavorites = WidgetPrefs.getFilterFavorites(context)
            val selectedIds     = WidgetPrefs.getSelectedContactIds(context)

            val rows = 3
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
            Log.d(TAG, "cols=$cols contacts=${contacts.size}")

            val layoutId = LAYOUTS[cols] ?: R.layout.widget_grid_4col
            val views = RemoteViews(context.packageName, layoutId)
            val pkg = context.packageName

            for (idx in 0 until maxTiles) {
                val tileId  = context.resources.getIdentifier("tile_${cols}_$idx",  "id", pkg)
                val photoId = context.resources.getIdentifier("photo_${cols}_$idx", "id", pkg)
                val nameId  = context.resources.getIdentifier("name_${cols}_$idx",  "id", pkg)

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
