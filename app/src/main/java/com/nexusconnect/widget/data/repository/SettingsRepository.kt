package com.nexusconnect.widget.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.nexusconnect.widget.data.models.*
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE)

    fun getSettings(): WidgetSettings {
        return WidgetSettings(
            columns = prefs.getInt("columns", 4),
            tileHeightDp = prefs.getInt("tile_height", 72),
            maxContacts = prefs.getInt("max_contacts", 12),
            showFavorites = prefs.getBoolean("show_favorites", true),
            showRecents = prefs.getBoolean("show_recents", true),
            showFrequent = prefs.getBoolean("show_frequent", false),
            globalClickAction = ClickAction.valueOf(
                prefs.getString("click_action", ClickAction.SHOW_DIALOG.name)!!
            ),
            priorityApp = MessengerApp.valueOf(
                prefs.getString("priority_app", MessengerApp.PHONE.name)!!
            ),
            hapticFeedback = prefs.getBoolean("haptic_feedback", true),
            theme = AppTheme.valueOf(
                prefs.getString("theme", AppTheme.LIGHT.name)!!
            ),
            dynamicColors = prefs.getBoolean("dynamic_colors", true),
            accentColor = prefs.getString("accent_color", "#1A56DB")!!,
            avatarStyle = AvatarStyle.valueOf(
                prefs.getString("avatar_style", AvatarStyle.DYNAMIC_INITIALS.name)!!
            )
        )
    }

    fun saveSettings(settings: WidgetSettings) {
        prefs.edit {
            putInt("columns", settings.columns)
            putInt("tile_height", settings.tileHeightDp)
            putInt("max_contacts", settings.maxContacts)
            putBoolean("show_favorites", settings.showFavorites)
            putBoolean("show_recents", settings.showRecents)
            putBoolean("show_frequent", settings.showFrequent)
            putString("click_action", settings.globalClickAction.name)
            putString("priority_app", settings.priorityApp.name)
            putBoolean("haptic_feedback", settings.hapticFeedback)
            putString("theme", settings.theme.name)
            putBoolean("dynamic_colors", settings.dynamicColors)
            putString("accent_color", settings.accentColor)
            putString("avatar_style", settings.avatarStyle.name)
        }
    }

    fun getSelectedContactIds(): List<String> {
        val json = prefs.getString("selected_contacts", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun saveSelectedContactIds(ids: List<String>) {
        val arr = JSONArray(ids)
        prefs.edit { putString("selected_contacts", arr.toString()) }
    }

    fun exportToJson(): String {
        val settings = getSettings()
        val obj = JSONObject().apply {
            put("columns", settings.columns)
            put("tileHeightDp", settings.tileHeightDp)
            put("maxContacts", settings.maxContacts)
            put("showFavorites", settings.showFavorites)
            put("showRecents", settings.showRecents)
            put("showFrequent", settings.showFrequent)
            put("clickAction", settings.globalClickAction.name)
            put("priorityApp", settings.priorityApp.name)
            put("hapticFeedback", settings.hapticFeedback)
            put("theme", settings.theme.name)
            put("dynamicColors", settings.dynamicColors)
            put("accentColor", settings.accentColor)
            put("avatarStyle", settings.avatarStyle.name)
            put("selectedContacts", JSONArray(getSelectedContactIds()))
        }
        return obj.toString(2)
    }

    fun importFromJson(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val settings = WidgetSettings(
                columns = obj.optInt("columns", 4),
                tileHeightDp = obj.optInt("tileHeightDp", 72),
                maxContacts = obj.optInt("maxContacts", 12),
                showFavorites = obj.optBoolean("showFavorites", true),
                showRecents = obj.optBoolean("showRecents", true),
                showFrequent = obj.optBoolean("showFrequent", false),
                globalClickAction = ClickAction.valueOf(obj.optString("clickAction", ClickAction.SHOW_DIALOG.name)),
                priorityApp = MessengerApp.valueOf(obj.optString("priorityApp", MessengerApp.PHONE.name)),
                hapticFeedback = obj.optBoolean("hapticFeedback", true),
                theme = AppTheme.valueOf(obj.optString("theme", AppTheme.LIGHT.name)),
                dynamicColors = obj.optBoolean("dynamicColors", true),
                accentColor = obj.optString("accentColor", "#1A56DB"),
                avatarStyle = AvatarStyle.valueOf(obj.optString("avatarStyle", AvatarStyle.DYNAMIC_INITIALS.name))
            )
            saveSettings(settings)
            val contacts = obj.optJSONArray("selectedContacts") ?: JSONArray()
            saveSelectedContactIds((0 until contacts.length()).map { contacts.getString(it) })
            true
        } catch (e: Exception) {
            false
        }
    }
}
