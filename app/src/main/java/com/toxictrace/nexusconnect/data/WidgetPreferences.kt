package com.toxictrace.nexusconnect.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_widget_prefs")

object WidgetPreferences {

    private val SELECTED_CONTACTS = stringSetPreferencesKey("selected_contacts")
    private val SHOW_CALL_ICONS = booleanPreferencesKey("show_call_icons")
    private val SHOW_UNKNOWN_NUMBERS = booleanPreferencesKey("show_unknown_numbers")
    private val TILE_SIZE = intPreferencesKey("tile_size")
    private val BACKGROUND_ALPHA = intPreferencesKey("background_alpha")
    private val TEXT_COLOR = intPreferencesKey("text_color")

    fun selectedContactsFlow(context: Context): Flow<Set<String>> =
        context.widgetDataStore.data.map { it[SELECTED_CONTACTS] ?: emptySet() }

    fun showCallIconsFlow(context: Context): Flow<Boolean> =
        context.widgetDataStore.data.map { it[SHOW_CALL_ICONS] ?: true }

    fun showUnknownNumbersFlow(context: Context): Flow<Boolean> =
        context.widgetDataStore.data.map { it[SHOW_UNKNOWN_NUMBERS] ?: true }

    suspend fun saveSelectedContacts(context: Context, contacts: Set<String>) {
        context.widgetDataStore.edit { it[SELECTED_CONTACTS] = contacts }
    }

    suspend fun migrateFromOldPrefs(context: Context) {
        val oldPrefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        if (oldPrefs.all.isNotEmpty()) {
            context.widgetDataStore.edit { prefs ->
                oldPrefs.getStringSet("selected_contacts", null)?.let { prefs[SELECTED_CONTACTS] = it }
                prefs[SHOW_CALL_ICONS] = oldPrefs.getBoolean("show_call_icons", true)
                prefs[SHOW_UNKNOWN_NUMBERS] = oldPrefs.getBoolean("show_unknown_numbers", true)
            }
        }
    }
}
