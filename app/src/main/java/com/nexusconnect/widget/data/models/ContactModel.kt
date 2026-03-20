package com.nexusconnect.widget.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ContactModel(
    val id: String,
    val name: String,
    val phone: String = "",
    val photoUri: String? = null,
    val isSelected: Boolean = false,
    val order: Int = 0,
    val clickAction: ClickAction = ClickAction.SHOW_DIALOG,
    val priorityApp: MessengerApp = MessengerApp.PHONE
) : Parcelable

enum class ClickAction(val label: String) {
    SHOW_DIALOG("Show selection dialog"),
    DIRECT_CALL("Direct Call"),
    OPEN_PROFILE("Open Profile")
}

enum class MessengerApp(val label: String) {
    PHONE("Phone"),
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    VIBER("Viber")
}
