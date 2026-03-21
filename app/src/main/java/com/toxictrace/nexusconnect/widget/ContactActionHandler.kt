package com.toxictrace.nexusconnect.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.toxictrace.nexusconnect.data.model.ClickAction
import com.toxictrace.nexusconnect.data.preferences.WidgetPrefs

object ContactActionHandler {

    fun handle(context: Context, contactId: Long, phone: String?, name: String?) {
        when (WidgetPrefs.getClickAction(context)) {
            ClickAction.SHOW_DIALOG -> showChooser(context, contactId, phone, name)
            ClickAction.DIRECT_CALL -> startDirectCall(context, phone)
        }
    }

    private fun startDirectCall(context: Context, phone: String?) {
        if (phone.isNullOrBlank()) return
        context.startActivity(
            Intent(context, DirectCallActivity::class.java).apply {
                putExtra("phone", phone)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                         Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                         Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
        )
    }

    private fun showChooser(context: Context, contactId: Long, phone: String?, name: String?) {
        context.startActivity(
            Intent(context, ContactChooserActivity::class.java).apply {
                putExtra("contact_id",    contactId)
                putExtra("contact_phone", phone ?: "")
                putExtra("contact_name",  name ?: "")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                         Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                         Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
        )
    }
}
