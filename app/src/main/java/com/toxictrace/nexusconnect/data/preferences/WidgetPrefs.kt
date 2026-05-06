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

    fun getShowCallTypeIcon(context: Context): Boolean =
        sp(context).getBoolean("show_call_type_icon", true)

    fun getCallIconStyle(context: Context): String =
        sp(context).getString("call_icon_style", "MATERIAL") ?: "MATERIAL"

    fun getShowUnknownNumbers(context: Context): Boolean =
        sp(context).getBoolean("show_unknown_numbers", true)

    fun getUnknownNumbersDays(context: Context): Int =
        sp(context).getInt("unknown_numbers_days", 3)

    fun getRecentsDays(context: Context): Int =
        sp(context).getInt("recents_days", 3)

    fun getShowCallLogButton(context: Context): Boolean =
        sp(context).getBoolean("show_call_log_button", false)

    fun getAvatarIdentity(context: Context): String =
        sp(context).getString("avatar_identity", "DEFAULT") ?: "DEFAULT"

    fun getCustomAvatarUri(context: Context): String =
        sp(context).getString("custom_avatar_uri", "") ?: ""

    fun getHapticFeedback(context: Context): Boolean =
        sp(context).getBoolean("haptic_feedback", true)

    fun getTheme(context: Context): String =
        sp(context).getString("theme", "LIGHT") ?: "LIGHT"

    fun getDynamicColors(context: Context): Boolean =
        sp(context).getBoolean("dynamic_colors", false)

    fun getAccentColorIndex(context: Context): Int =
        sp(context).getInt("accent_color_index", 0)

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
            putBoolean("haptic_feedback",    settings.hapticFeedback)
            putString("avatar_identity",       settings.avatarIdentity.name)
            putString("custom_avatar_uri",      settings.customAvatarUri)
            putBoolean("show_unknown_numbers",  settings.showUnknownNumbers)
            putInt("unknown_numbers_days",      settings.unknownNumbersDays)
            putBoolean("show_call_type_icon",   settings.showCallTypeIcon)
            putString("call_icon_style",        settings.callIconStyle)
            putString("click_action",        settings.clickAction.name)
            putString("theme",              settings.theme.name)
            putBoolean("dynamic_colors",    settings.dynamicColors)
            putInt("accent_color_index",    settings.accentColorIndex)
            putString("messenger_whatsapp", settings.messengerWhatsApp)
            putString("messenger_viber",    settings.messengerViber)
            putString("messenger_telegram", settings.messengerTelegram)
            putString("selected_ids",      selectedIds.joinToString(","))
            putInt("recents_days",          settings.recentsDays)
            putBoolean("show_call_log_button", settings.showCallLogButton)
            apply()
        }
    }

    fun saveSelectedIds(context: Context, ids: List<Long>) {
        sp(context).edit()
            .putString("selected_ids", ids.joinToString(","))
            .apply()
    }
}
