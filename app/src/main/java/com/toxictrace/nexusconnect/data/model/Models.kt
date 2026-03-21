package com.toxictrace.nexusconnect.data.model

import android.net.Uri

data class Contact(
    val id: Long,
    val name: String,
    val photoUri: Uri? = null,
    val phoneNumber: String? = null,
    val isSelected: Boolean = false,
    val isStarred: Boolean = false,
    val sortOrder: Int = 0,
    val customAvatarIndex: Int = -1  // -1 = use default/initials, 0 = silhouette, 1-5 = vector avatars
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
    DEFAULT,   // silhouette image
    CUSTOM     // user-picked image from gallery
}

enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}
