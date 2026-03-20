package com.nexusconnect.widget.data.models

data class WidgetSettings(
    val columns: Int = 4,
    val tileHeightDp: Int = 72,
    val maxContacts: Int = 12,
    val showFavorites: Boolean = true,
    val showRecents: Boolean = true,
    val showFrequent: Boolean = false,
    val globalClickAction: ClickAction = ClickAction.SHOW_DIALOG,
    val priorityApp: MessengerApp = MessengerApp.PHONE,
    val hapticFeedback: Boolean = true,
    val theme: AppTheme = AppTheme.LIGHT,
    val dynamicColors: Boolean = true,
    val accentColor: String = "#1A56DB",
    val avatarStyle: AvatarStyle = AvatarStyle.DYNAMIC_INITIALS
)

enum class AppTheme(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System")
}

enum class AvatarStyle(val label: String) {
    SYSTEM_DEFAULT("System Default"),
    DYNAMIC_INITIALS("Dynamic Initials"),
    PHOTOS_ONLY("Photos Only")
}
