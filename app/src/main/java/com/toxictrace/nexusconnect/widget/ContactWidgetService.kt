package com.toxictrace.nexusconnect.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.toxictrace.nexusconnect.R
import com.toxictrace.nexusconnect.data.model.Contact
import com.toxictrace.nexusconnect.data.preferences.SettingsRepository
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import com.toxictrace.nexusconnect.data.repository.ContactsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// Kept for manifest compatibility — actual widget rendering is in ContactWidgetProvider
class ContactWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return ContactWidgetFactory(applicationContext, widgetId)
    }
}

class ContactWidgetFactory(
    private val context: Context,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var contacts: List<Contact> = emptyList()
    private var settings: WidgetSettings = WidgetSettings()
    private val contactsRepo = ContactsRepository(context)
    private val settingsRepo = SettingsRepository(context)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        settings = runBlocking { settingsRepo.settings.first() }
        val allContacts = contactsRepo.loadContacts()
        val selectedIds = runBlocking { settingsRepo.getSelectedContactIds() }
        contacts = when {
            selectedIds.isNotEmpty() ->
                selectedIds.mapNotNull { id -> allContacts.firstOrNull { it.id == id } }
                    .take(settings.maxContacts)
            settings.filterFavorites ->
                allContacts.filter { it.isStarred }.take(settings.maxContacts)
            else ->
                allContacts.take(settings.maxContacts)
        }
    }

    override fun onDestroy() { contacts = emptyList() }
    override fun getCount(): Int = contacts.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(p: Int): Long = contacts.getOrNull(p)?.id ?: p.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val contact = contacts.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_tile)

        val views = RemoteViews(context.packageName, R.layout.widget_tile)
        views.setTextViewText(R.id.tile_name,
            contact.name.split(" ").firstOrNull() ?: contact.name)

        val bmp = loadPhoto(contact, 120) ?: makeInitials(contact, 120)
        views.setImageViewBitmap(R.id.tile_photo, bmp)

        return views
    }

    private fun loadPhoto(contact: Contact, maxPx: Int): Bitmap? {
        val uriStr = contact.photoUri ?: return null
        val uri = android.net.Uri.parse(uriStr)
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
        canvas.drawText(initials, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        return bmp
    }
}
