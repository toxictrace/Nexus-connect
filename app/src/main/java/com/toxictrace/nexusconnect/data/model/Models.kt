package com.toxictrace.nexusconnect.data.model

import androidx.compose.runtime.Stable

@Stable
data class Contact(
    val id: Long,
    val name: String,
    val photoUri: String? = null,   // String instead of Uri — stable for Compose
    val phoneNumber: String? = null,
    val isSelected: Boolean = false,
    val isStarred: Boolean = false,
    val sortOrder: Int = 0
)

enum class ClickAction { SHOW_DIALOG, DIRECT_CALL }
enum class FilterMode  { FAVORITES, RECENTS, FREQUENT }
enum class AvatarIdentity { DEFAULT, CUSTOM }
enum class AppTheme { LIGHT, DARK, SYSTEM }
