package com.toxictrace.nexusconnect.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.toxictrace.nexusconnect.R
import com.toxictrace.nexusconnect.data.model.Contact
import com.toxictrace.nexusconnect.data.model.PriorityApp
import com.toxictrace.nexusconnect.data.preferences.SettingsRepository
import com.toxictrace.nexusconnect.data.preferences.WidgetSettings
import com.toxictrace.nexusconnect.data.repository.ContactsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ContactWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
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
        // Called on background thread — safe to do IO
        settings = runBlocking { settingsRepo.settings.first() }

        val allContacts = contactsRepo.loadContacts()
        val selectedIds = runBlocking { settingsRepo.getSelectedContactIds() }

        contacts = if (selectedIds.isEmpty()) {
            // Fallback: show starred contacts
            allContacts.filter { it.isStarred }.take(settings.maxContacts)
        } else {
            // Show contacts in user-defined order
            selectedIds.mapNotNull { id -> allContacts.firstOrNull { it.id == id } }
                .take(settings.maxContacts)
        }
    }

    override fun onDestroy() {
        contacts = emptyList()
    }

    override fun getCount(): Int = contacts.size

    override fun getViewAt(position: Int): RemoteViews {
        val contact = contacts.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_tile)

        val views = RemoteViews(context.packageName, R.layout.widget_tile)

        // Set name
        views.setTextViewText(R.id.tile_name, contact.name.split(" ").firstOrNull() ?: contact.name)

        // Set photo or initials bitmap
        val bitmap = loadContactPhoto(contact)
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.tile_photo, bitmap)
        } else {
            views.setImageViewBitmap(R.id.tile_photo, makeInitialsBitmap(contact))
        }

        // Messenger icon
        val messengerIconRes = when (contact.priorityApp) {
            PriorityApp.PHONE    -> android.R.drawable.ic_menu_call
            PriorityApp.WHATSAPP -> android.R.drawable.ic_menu_send
            PriorityApp.TELEGRAM -> android.R.drawable.ic_menu_send
            PriorityApp.VIBER    -> android.R.drawable.ic_menu_call
        }
        views.setImageViewResource(R.id.tile_messenger_icon, messengerIconRes)

        // Fill-in intent for click (merged with template PendingIntent)
        val fillIn = Intent().apply {
            putExtra(ContactWidgetProvider.EXTRA_CONTACT_ID,    contact.id)
            putExtra(ContactWidgetProvider.EXTRA_CONTACT_PHONE, contact.phoneNumber ?: "")
            putExtra(ContactWidgetProvider.EXTRA_CONTACT_NAME,  contact.name)
        }
        views.setOnClickFillInIntent(R.id.tile_photo, fillIn)
        views.setOnClickFillInIntent(R.id.tile_name,  fillIn)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = contacts.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds(): Boolean = true

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun loadContactPhoto(contact: Contact): Bitmap? {
        val uri = contact.photoUri ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun makeInitialsBitmap(contact: Contact): Bitmap {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColors = intArrayOf(
            Color.parseColor("#1A3CA8"),
            Color.parseColor("#7B3FA0"),
            Color.parseColor("#007A6E"),
            Color.parseColor("#8B2252"),
            Color.parseColor("#2E7D32"),
            Color.parseColor("#B85C00")
        )
        val bgColor = bgColors[(contact.id % bgColors.size).toInt()]

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = bgColor
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        val initials = contact.name.split(" ")
            .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")

        paint.color = Color.WHITE
        paint.textSize = size * 0.35f
        paint.textAlign = Paint.Align.CENTER
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(initials, size / 2f, y, paint)

        return bitmap
    }
}
