package com.toxictrace.nexusconnect.data.model

import android.net.Uri

data class Contact(
    val id: Long,
    val name: String,
    val photoUri: Uri? = null,
    val phoneNumber: String? = null,
    val isSelected: Boolean = false,
    val isStarred: Boolean = false,
    val sortOrder: Int = 0
)

enum class ClickAction {
    SHOW_DIALOG,
    DIRECT_CALL
}

enum class FilterMode {
    FAVORITES,
    RECENTS,
    FREQUENT
}

enum class AvatarIdentity {
    SYSTEM_DEFAULT,
    DYNAMIC_INITIALS,
    PHOTOS_ONLY
}

enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}
