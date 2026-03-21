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
    val tileHeightDp: Int = 72,
    val maxContacts: Int = 12,
    val filterFavorites: Boolean = true,
    val filterRecents: Boolean = true,
    val filterFrequent: Boolean = false,
    val clickAction: ClickAction = ClickAction.SHOW_DIALOG,
    val priorityApp: PriorityApp = PriorityApp.PHONE,
    val hapticFeedback: Boolean = true,
    val theme: AppTheme = AppTheme.LIGHT,
    val dynamicColors: Boolean = true,
    val avatarIdentity: AvatarIdentity = AvatarIdentity.DYNAMIC_INITIALS,
    val accentColorIndex: Int = 0
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val COLUMNS = intPreferencesKey("columns")
        val TILE_HEIGHT = intPreferencesKey("tile_height")
        val MAX_CONTACTS = intPreferencesKey("max_contacts")
        val FILTER_FAVORITES = booleanPreferencesKey("filter_favorites")
        val FILTER_RECENTS = booleanPreferencesKey("filter_recents")
        val FILTER_FREQUENT = booleanPreferencesKey("filter_frequent")
        val CLICK_ACTION = stringPreferencesKey("click_action")
        val PRIORITY_APP = stringPreferencesKey("priority_app")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val AVATAR_IDENTITY = stringPreferencesKey("avatar_identity")
        val ACCENT_COLOR_INDEX = intPreferencesKey("accent_color_index")
        // Ordered list of selected contact IDs, stored as comma-separated string
        val SELECTED_CONTACT_IDS = stringPreferencesKey("selected_contact_ids")
    }

    val settings: Flow<WidgetSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            WidgetSettings(
                columns = prefs[Keys.COLUMNS] ?: 4,
                tileHeightDp = prefs[Keys.TILE_HEIGHT] ?: 72,
                maxContacts = prefs[Keys.MAX_CONTACTS] ?: 12,
                filterFavorites = prefs[Keys.FILTER_FAVORITES] ?: true,
                filterRecents = prefs[Keys.FILTER_RECENTS] ?: true,
                filterFrequent = prefs[Keys.FILTER_FREQUENT] ?: false,
                clickAction = prefs[Keys.CLICK_ACTION]
                    ?.let { runCatching { ClickAction.valueOf(it) }.getOrNull() }
                    ?: ClickAction.SHOW_DIALOG,
                priorityApp = prefs[Keys.PRIORITY_APP]
                    ?.let { runCatching { PriorityApp.valueOf(it) }.getOrNull() }
                    ?: PriorityApp.PHONE,
                hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
                theme = prefs[Keys.THEME]
                    ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                    ?: AppTheme.LIGHT,
                dynamicColors = prefs[Keys.DYNAMIC_COLORS] ?: true,
                avatarIdentity = prefs[Keys.AVATAR_IDENTITY]
                    ?.let { runCatching { AvatarIdentity.valueOf(it) }.getOrNull() }
                    ?: AvatarIdentity.DYNAMIC_INITIALS,
                accentColorIndex = prefs[Keys.ACCENT_COLOR_INDEX] ?: 0
            )
        }

    suspend fun updateSettings(update: WidgetSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COLUMNS] = update.columns
            prefs[Keys.TILE_HEIGHT] = update.tileHeightDp
            prefs[Keys.MAX_CONTACTS] = update.maxContacts
            prefs[Keys.FILTER_FAVORITES] = update.filterFavorites
            prefs[Keys.FILTER_RECENTS] = update.filterRecents
            prefs[Keys.FILTER_FREQUENT] = update.filterFrequent
            prefs[Keys.CLICK_ACTION] = update.clickAction.name
            prefs[Keys.PRIORITY_APP] = update.priorityApp.name
            prefs[Keys.HAPTIC_FEEDBACK] = update.hapticFeedback
            prefs[Keys.THEME] = update.theme.name
            prefs[Keys.DYNAMIC_COLORS] = update.dynamicColors
            prefs[Keys.AVATAR_IDENTITY] = update.avatarIdentity.name
            prefs[Keys.ACCENT_COLOR_INDEX] = update.accentColorIndex
        }
        // Keep WidgetPrefs in sync for synchronous widget access
        val selectedIds = getSelectedContactIds()
        WidgetPrefs.sync(context, update, selectedIds)
    }

    /** Returns ordered list of selected contact IDs */
    suspend fun getSelectedContactIds(): List<Long> {
        val prefs = context.dataStore.data.first()
        val raw = prefs[Keys.SELECTED_CONTACT_IDS] ?: return emptyList()
        return raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    /** Saves ordered list of selected contact IDs */
    suspend fun saveSelectedContactIds(ids: List<Long>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_CONTACT_IDS] = ids.joinToString(",")
        }
        // Also sync to WidgetPrefs
        WidgetPrefs.saveSelectedIds(context, ids)
    }
}
