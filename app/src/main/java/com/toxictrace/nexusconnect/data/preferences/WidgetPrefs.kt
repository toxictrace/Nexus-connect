package com.toxictrace.nexusconnect.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.toxictrace.nexusconnect.data.model.ClickAction

object WidgetPrefs {
    private const val NAME = "nexus_widget_prefs"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getColumns(context: Context): Int =
        sp(context).getInt("columns", 4).coerceIn(3, 6)

    fun getRows(context: Context): Int =
        sp(context).getInt("rows", 3).coerceIn(3, 6)

    fun getMaxContacts(context: Context): Int =
        sp(context).getInt("max_contacts", 12)

    fun getFilterFavorites(context: Context): Boolean =
        sp(context).getBoolean("filter_favorites", true)

    fun getFilterFrequent(context: Context): Boolean =
        sp(context).getBoolean("filter_frequent", false)

    fun getFilterRecents(context: Context): Boolean =
        sp(context).getBoolean("filter_recents", true)

    fun getClickAction(context: Context): ClickAction =
        runCatching {
            ClickAction.valueOf(sp(context).getString("click_action", "") ?: "")
        }.getOrDefault(ClickAction.SHOW_DIALOG)

    fun getSelectedContactIds(context: Context): List<Long> {
        val raw = sp(context).getString("selected_ids", "") ?: ""
        return if (raw.isBlank()) emptyList()
        else raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    fun getMessengerWhatsApp(context: Context): String = sp(context).getString("messenger_whatsapp", "") ?: ""
    fun getMessengerViber(context: Context):    String = sp(context).getString("messenger_viber", "")    ?: ""
    fun getMessengerTelegram(context: Context): String = sp(context).getString("messenger_telegram", "") ?: ""

    fun sync(context: Context, settings: WidgetSettings, selectedIds: List<Long>) {
        sp(context).edit().apply {
            putInt("columns",              settings.columns)
            putInt("rows",                 settings.tileHeightDp)
            putInt("max_contacts",         settings.maxContacts)
            putBoolean("filter_favorites", settings.filterFavorites)
            putBoolean("filter_recents",   settings.filterRecents)
            putBoolean("filter_frequent",  settings.filterFrequent)
            putString("click_action",      settings.clickAction.name)
            putString("messenger_whatsapp", settings.messengerWhatsApp)
            putString("messenger_viber",    settings.messengerViber)
            putString("messenger_telegram", settings.messengerTelegram)
            putString("selected_ids",      selectedIds.joinToString(","))
            apply()
        }
    }

    fun saveSelectedIds(context: Context, ids: List<Long>) {
        sp(context).edit()
            .putString("selected_ids", ids.joinToString(","))
            .apply()
    }
}
