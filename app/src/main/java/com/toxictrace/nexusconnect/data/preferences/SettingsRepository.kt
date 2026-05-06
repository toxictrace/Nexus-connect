package com.toxictrace.nexusconnect.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.toxictrace.nexusconnect.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_settings")

data class WidgetSettings(
    val columns: Int = 4,
    val tileHeightDp: Int = 3,
    val maxContacts: Int = 12,
    val filterFavorites: Boolean = true,
    val filterRecents: Boolean = true,
    val filterFrequent: Boolean = false,
    val clickAction: ClickAction = ClickAction.SHOW_DIALOG,
    val hapticFeedback: Boolean = true,
    val theme: AppTheme = AppTheme.LIGHT,
    val dynamicColors: Boolean = true,
    val accentColorIndex: Int = 0,
    val avatarIdentity: AvatarIdentity = AvatarIdentity.DEFAULT,
    val customAvatarUri: String = "",
    val showUnknownNumbers: Boolean = true,
    val unknownNumbersDays: Int = 3,
    val messengerWhatsApp: String = "",
    val messengerViber: String = "",
    val messengerTelegram: String = "",
    val showCallTypeIcon: Boolean = true,
    val callIconStyle: String = "MATERIAL", // NONE, MATERIAL, GLASS
    val backupFolderUri: String = "",
    val language: String = "system",
    val recentsDays: Int = 3,
    val showCallLogButton: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val COLUMNS              = intPreferencesKey("columns")
        val TILE_HEIGHT          = intPreferencesKey("tile_height")
        val MAX_CONTACTS         = intPreferencesKey("max_contacts")
        val FILTER_FAVORITES     = booleanPreferencesKey("filter_favorites")
        val FILTER_RECENTS       = booleanPreferencesKey("filter_recents")
        val FILTER_FREQUENT      = booleanPreferencesKey("filter_frequent")
        val CLICK_ACTION         = stringPreferencesKey("click_action")
        val HAPTIC_FEEDBACK      = booleanPreferencesKey("haptic_feedback")
        val THEME                = stringPreferencesKey("theme")
        val DYNAMIC_COLORS       = booleanPreferencesKey("dynamic_colors")
        val ACCENT_COLOR_INDEX   = intPreferencesKey("accent_color_index")
        val AVATAR_IDENTITY      = stringPreferencesKey("avatar_identity")
        val CUSTOM_AVATAR_URI    = stringPreferencesKey("custom_avatar_uri")
        val SHOW_UNKNOWN_NUMBERS = booleanPreferencesKey("show_unknown_numbers")
        val UNKNOWN_NUMBERS_DAYS = intPreferencesKey("unknown_numbers_days")
        val MESSENGER_WHATSAPP   = stringPreferencesKey("messenger_whatsapp")
        val MESSENGER_VIBER      = stringPreferencesKey("messenger_viber")
        val MESSENGER_TELEGRAM   = stringPreferencesKey("messenger_telegram")
        val SHOW_CALL_TYPE_ICON  = booleanPreferencesKey("show_call_type_icon")
        val CALL_ICON_STYLE      = stringPreferencesKey("call_icon_style")
        val BACKUP_FOLDER_URI    = stringPreferencesKey("backup_folder_uri")
        val LANGUAGE             = stringPreferencesKey("language")
        val RECENTS_DAYS         = intPreferencesKey("recents_days")
        val SHOW_CALL_LOG_BUTTON = booleanPreferencesKey("show_call_log_button")
        val SELECTED_CONTACT_IDS = stringPreferencesKey("selected_contact_ids")
    }

    val settings: Flow<WidgetSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            WidgetSettings(
                columns            = prefs[Keys.COLUMNS] ?: 4,
                tileHeightDp       = prefs[Keys.TILE_HEIGHT] ?: 3,
                maxContacts        = prefs[Keys.MAX_CONTACTS] ?: 12,
                filterFavorites    = prefs[Keys.FILTER_FAVORITES] ?: true,
                filterRecents      = prefs[Keys.FILTER_RECENTS] ?: true,
                filterFrequent     = prefs[Keys.FILTER_FREQUENT] ?: false,
                clickAction        = prefs[Keys.CLICK_ACTION]
                    ?.let { runCatching { ClickAction.valueOf(it) }.getOrNull() }
                    ?: ClickAction.SHOW_DIALOG,
                hapticFeedback     = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
                theme              = prefs[Keys.THEME]
                    ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                    ?: AppTheme.LIGHT,
                dynamicColors      = prefs[Keys.DYNAMIC_COLORS] ?: true,
                accentColorIndex   = prefs[Keys.ACCENT_COLOR_INDEX] ?: 0,
                avatarIdentity     = prefs[Keys.AVATAR_IDENTITY]
                    ?.let { runCatching { AvatarIdentity.valueOf(it) }.getOrNull() }
                    ?: AvatarIdentity.DEFAULT,
                customAvatarUri    = prefs[Keys.CUSTOM_AVATAR_URI] ?: "",
                showUnknownNumbers = prefs[Keys.SHOW_UNKNOWN_NUMBERS] ?: true,
                unknownNumbersDays = prefs[Keys.UNKNOWN_NUMBERS_DAYS] ?: 3,
                messengerWhatsApp  = prefs[Keys.MESSENGER_WHATSAPP] ?: "",
                messengerViber     = prefs[Keys.MESSENGER_VIBER] ?: "",
                messengerTelegram  = prefs[Keys.MESSENGER_TELEGRAM] ?: "",
                showCallTypeIcon   = prefs[Keys.SHOW_CALL_TYPE_ICON] ?: true,
                callIconStyle      = prefs[Keys.CALL_ICON_STYLE] ?: "MATERIAL",
                backupFolderUri    = prefs[Keys.BACKUP_FOLDER_URI] ?: "",
                language           = prefs[Keys.LANGUAGE] ?: "system",
                recentsDays        = prefs[Keys.RECENTS_DAYS] ?: 3,
                showCallLogButton  = prefs[Keys.SHOW_CALL_LOG_BUTTON] ?: false
            )
        }

    suspend fun updateSettings(update: WidgetSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COLUMNS]              = update.columns
            prefs[Keys.TILE_HEIGHT]          = update.tileHeightDp
            prefs[Keys.MAX_CONTACTS]         = update.maxContacts
            prefs[Keys.FILTER_FAVORITES]     = update.filterFavorites
            prefs[Keys.FILTER_RECENTS]       = update.filterRecents
            prefs[Keys.FILTER_FREQUENT]      = update.filterFrequent
            prefs[Keys.CLICK_ACTION]         = update.clickAction.name
            prefs[Keys.HAPTIC_FEEDBACK]      = update.hapticFeedback
            prefs[Keys.THEME]                = update.theme.name
            prefs[Keys.DYNAMIC_COLORS]       = update.dynamicColors
            prefs[Keys.ACCENT_COLOR_INDEX]   = update.accentColorIndex
            prefs[Keys.AVATAR_IDENTITY]      = update.avatarIdentity.name
            prefs[Keys.CUSTOM_AVATAR_URI]    = update.customAvatarUri
            prefs[Keys.SHOW_UNKNOWN_NUMBERS] = update.showUnknownNumbers
            prefs[Keys.UNKNOWN_NUMBERS_DAYS] = update.unknownNumbersDays
            prefs[Keys.MESSENGER_WHATSAPP]   = update.messengerWhatsApp
            prefs[Keys.MESSENGER_VIBER]      = update.messengerViber
            prefs[Keys.MESSENGER_TELEGRAM]   = update.messengerTelegram
            prefs[Keys.SHOW_CALL_TYPE_ICON]  = update.showCallTypeIcon
            prefs[Keys.CALL_ICON_STYLE]      = update.callIconStyle
            prefs[Keys.BACKUP_FOLDER_URI]    = update.backupFolderUri
            prefs[Keys.LANGUAGE]             = update.language
            prefs[Keys.RECENTS_DAYS]         = update.recentsDays
            prefs[Keys.SHOW_CALL_LOG_BUTTON] = update.showCallLogButton
        }
        val selectedIds = getSelectedContactIds()
        WidgetPrefs.sync(context, update, selectedIds)
    }

    suspend fun getSelectedContactIds(): List<Long> {
        val prefs = context.dataStore.data.first()
        val raw = prefs[Keys.SELECTED_CONTACT_IDS] ?: return emptyList()
        return raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    suspend fun saveSelectedContactIds(ids: List<Long>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_CONTACT_IDS] = ids.joinToString(",")
        }
        WidgetPrefs.saveSelectedIds(context, ids)
    }
}
