package com.toxictrace.nexusconnect.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.toxictrace.nexusconnect.data.model.ClickAction
import com.toxictrace.nexusconnect.data.model.PriorityApp
import com.toxictrace.nexusconnect.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object ContactActionHandler {

    fun handle(context: Context, contactId: Long, phone: String?, name: String?) {
        val settings = runBlocking { SettingsRepository(context).settings.first() }

        when (settings.clickAction) {
            ClickAction.DIRECT_CALL  -> dialDirectly(context, phone, settings.priorityApp)
            ClickAction.OPEN_PROFILE -> openProfile(context, contactId)
            ClickAction.SHOW_DIALOG  -> showChooserActivity(context, contactId, phone, name)
        }
    }

    /** Direct dial or open messenger */
    private fun dialDirectly(context: Context, phone: String?, app: PriorityApp) {
        val intent = when (app) {
            PriorityApp.PHONE -> Intent(Intent.ACTION_CALL, Uri.parse("tel:${phone}"))
            PriorityApp.WHATSAPP -> whatsAppIntent(phone)
            PriorityApp.TELEGRAM -> telegramIntent(phone)
            PriorityApp.VIBER    -> viberIntent(phone)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        runCatching { context.startActivity(intent) }
            .onFailure {
                // Fallback to phone call
                val fallback = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            }
    }

    /** Open system contact profile */
    private fun openProfile(context: Context, contactId: Long) {
        val uri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_URI, contactId.toString()
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Launch our chooser screen for this contact */
    private fun showChooserActivity(
        context: Context, contactId: Long, phone: String?, name: String?
    ) {
        val intent = Intent(context, ContactChooserActivity::class.java).apply {
            putExtra("contact_id",    contactId)
            putExtra("contact_phone", phone ?: "")
            putExtra("contact_name",  name ?: "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // ── Messenger deep links ──────────────────────────────────────────────────

    private fun whatsAppIntent(phone: String?): Intent {
        val cleaned = phone?.replace(Regex("[^+\\d]"), "") ?: ""
        return Intent(Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/$cleaned"))
    }

    private fun telegramIntent(phone: String?): Intent {
        val cleaned = phone?.replace(Regex("[^+\\d]"), "") ?: ""
        return Intent(Intent.ACTION_VIEW,
            Uri.parse("tg://resolve?phone=$cleaned"))
    }

    private fun viberIntent(phone: String?): Intent {
        val cleaned = phone?.replace(Regex("[^+\\d]"), "") ?: ""
        return Intent(Intent.ACTION_VIEW,
            Uri.parse("viber://chat?number=$cleaned"))
    }
}
