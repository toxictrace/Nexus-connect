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
import com.toxictrace.nexusconnect.data.preferences.SettingsRepository
import com.toxictrace.nexusconnect.data.repository.ContactsRepository
import com.toxictrace.nexusconnect.data.model.Contact
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ContactWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "NexusWidget"
        const val ACTION_CONTACT_CLICK = "com.toxictrace.nexusconnect.ACTION_CONTACT_CLICK"
        const val EXTRA_CONTACT_ID    = "extra_contact_id"
        const val EXTRA_CONTACT_PHONE = "extra_contact_phone"
        const val EXTRA_CONTACT_NAME  = "extra_contact_name"

        // tile_N, photo_N, name_N — all declared in ids.xml
        private val TILE_IDS = intArrayOf(
            R.id.tile_0,  R.id.tile_1,  R.id.tile_2,  R.id.tile_3,
            R.id.tile_4,  R.id.tile_5,  R.id.tile_6,  R.id.tile_7,
            R.id.tile_8,  R.id.tile_9,  R.id.tile_10, R.id.tile_11
        )
        private val PHOTO_IDS = intArrayOf(
            R.id.photo_0,  R.id.photo_1,  R.id.photo_2,  R.id.photo_3,
            R.id.photo_4,  R.id.photo_5,  R.id.photo_6,  R.id.photo_7,
            R.id.photo_8,  R.id.photo_9,  R.id.photo_10, R.id.photo_11
        )
        private val NAME_IDS = intArrayOf(
            R.id.name_0,  R.id.name_1,  R.id.name_2,  R.id.name_3,
            R.id.name_4,  R.id.name_5,  R.id.name_6,  R.id.name_7,
            R.id.name_8,  R.id.name_9,  R.id.name_10, R.id.name_11
        )

        fun updateAllWidgets(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ContactWidgetProvider::class.java))
            Log.d(TAG, "updateAllWidgets: ${ids.size} widgets")
            ids.forEach { buildAndPush(context, mgr, it) }
        }

        fun buildAndPush(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            Log.d(TAG, "buildAndPush id=$widgetId")
            val settingsRepo = SettingsRepository(context)
            val contactsRepo = ContactsRepository(context)
            val settings     = runBlocking { settingsRepo.settings.first() }
            val allContacts  = contactsRepo.loadContacts()
            val selectedIds  = runBlocking { settingsRepo.getSelectedContactIds() }

            val contacts = when {
                selectedIds.isNotEmpty() ->
                    selectedIds.mapNotNull { id -> allContacts.firstOrNull { it.id == id } }.take(12)
                settings.filterFavorites ->
                    allContacts.filter { it.isStarred }.take(12)
                else ->
                    allContacts.take(12)
            }
            Log.d(TAG, "contacts to show: ${contacts.size}")

            val views = RemoteViews(context.packageName, R.layout.widget_grid_4x3)

            for (idx in 0..11) {
                val contact = contacts.getOrNull(idx)
                if (contact != null) {
                    // Show tile
                    views.setViewVisibility(TILE_IDS[idx], View.VISIBLE)

                    // Photo or initials bitmap
                    val bmp = loadPhoto(context, contact, 120) ?: makeInitials(contact, 120)
                    views.setImageViewBitmap(PHOTO_IDS[idx], bmp)

                    // Name
                    val firstName = contact.name.split(" ").firstOrNull() ?: contact.name
                    views.setTextViewText(NAME_IDS[idx], firstName)

                    // PendingIntent — unique per contact via data URI
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

            mgr.updateAppWidget(widgetId, views)
            Log.d(TAG, "buildAndPush done")
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
            } catch (e: Exception) {
                Log.w(TAG, "loadPhoto failed: ${e.message}")
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
            canvas.drawText(initials, size / 2f,
                size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
            return bmp
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { buildAndPush(context, mgr, it) }
    }
    override fun onEnabled(context: Context) { ContactsObserverService.start(context) }
    override fun onDisabled(context: Context) { ContactsObserverService.stop(context) }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CONTACT_CLICK) {
            val id    = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
            val phone = intent.getStringExtra(EXTRA_CONTACT_PHONE)
            val name  = intent.getStringExtra(EXTRA_CONTACT_NAME)
            Log.d(TAG, "Click: $name id=$id")
            ContactActionHandler.handle(context, id, phone, name)
        }
    }
}
