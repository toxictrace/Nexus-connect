package com.toxictrace.nexusconnect.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.toxictrace.nexusconnect.data.model.ClickAction
import com.toxictrace.nexusconnect.data.model.PriorityApp

/**
 * Synchronous SharedPreferences mirror — used ONLY by the widget (BroadcastReceiver context).
 * The UI uses DataStore (SettingsRepository). Both write to their own store;
 * SettingsRepository.updateSettings() calls WidgetPrefs.sync() to keep them aligned.
 */
object WidgetPrefs {
    private const val NAME = "nexus_widget_prefs"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ── Read (synchronous, safe from any thread) ──────────────────────────────

    fun getColumns(context: Context): Int =
        sp(context).getInt("columns", 4).coerceIn(3, 6)

    fun getRows(context: Context): Int =
        sp(context).getInt("rows", 3).coerceIn(2, 4)

    fun getMaxContacts(context: Context): Int =
        sp(context).getInt("max_contacts", 12)

    fun getFilterFavorites(context: Context): Boolean =
        sp(context).getBoolean("filter_favorites", true)

    fun getClickAction(context: Context): ClickAction =
        runCatching {
            ClickAction.valueOf(sp(context).getString("click_action", "") ?: "")
        }.getOrDefault(ClickAction.SHOW_DIALOG)

    fun getPriorityApp(context: Context): PriorityApp =
        runCatching {
            PriorityApp.valueOf(sp(context).getString("priority_app", "") ?: "")
        }.getOrDefault(PriorityApp.PHONE)

    fun getSelectedContactIds(context: Context): List<Long> {
        val raw = sp(context).getString("selected_ids", "") ?: ""
        return if (raw.isBlank()) emptyList()
               else raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    // ── Write (called from SettingsRepository after DataStore update) ─────────

    fun sync(context: Context, settings: WidgetSettings, selectedIds: List<Long>) {
        sp(context).edit().apply {
            putInt("columns",          settings.columns)
            putInt("rows",             settings.tileHeightDp) // reuse tileHeightDp field as rows (2-4)
            putInt("max_contacts",     settings.maxContacts)
            putBoolean("filter_favorites", settings.filterFavorites)
            putBoolean("filter_recents",   settings.filterRecents)
            putBoolean("filter_frequent",  settings.filterFrequent)
            putString("click_action",  settings.clickAction.name)
            putString("priority_app",  settings.priorityApp.name)
            putString("selected_ids",  selectedIds.joinToString(","))
            apply()
        }
    }

    fun saveSelectedIds(context: Context, ids: List<Long>) {
        sp(context).edit()
            .putString("selected_ids", ids.joinToString(","))
            .apply()
    }
}
