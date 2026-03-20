package com.nexusconnect.widget.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.nexusconnect.widget.R
import com.nexusconnect.widget.data.repository.ContactsRepository
import com.nexusconnect.widget.data.repository.SettingsRepository

class ContactWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ContactWidgetFactory(applicationContext, intent)
    }
}

class ContactWidgetFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val settingsRepo = SettingsRepository(context)
    private val contactsRepo = ContactsRepository(context)
    private val contacts = mutableListOf<com.nexusconnect.widget.data.models.ContactModel>()

    override fun onCreate() { loadContacts() }

    override fun onDataSetChanged() { loadContacts() }

    private fun loadContacts() {
        val settings = settingsRepo.getSettings()
        val selectedIds = settingsRepo.getSelectedContactIds()
        val all = contactsRepo.getAllContacts()
        val selected = selectedIds.mapNotNull { id -> all.find { it.id == id } }
        contacts.clear()
        contacts.addAll(selected.take(settings.maxContacts))
    }

    override fun getCount() = contacts.size

    override fun getViewAt(position: Int): RemoteViews {
        val contact = contacts[position]
        val views = RemoteViews(context.packageName, R.layout.item_widget_tile)

        views.setTextViewText(R.id.tvTileName, contact.name.split(" ").first())

        val initials = contact.name.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
        views.setTextViewText(R.id.tvTileInitials, initials)

        val fillIntent = Intent().putExtra("contact_id", contact.id)
        views.setOnClickFillInIntent(R.id.tileRoot, fillIntent)

        return views
    }

    override fun getLoadingView() = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = contacts[position].id.hashCode().toLong()
    override fun hasStableIds() = true
    override fun onDestroy() {}
}
