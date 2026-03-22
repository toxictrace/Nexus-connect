package com.toxictrace.nexusconnect.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    fun applyLocale(context: Context, language: String): Context {
        val locale = when (language) {
            "en" -> Locale.ENGLISH
            "ru" -> Locale("ru")
            else -> Locale.getDefault() // system
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("nexus_ui_prefs", Context.MODE_PRIVATE)
        return prefs.getString("language", "system") ?: "system"
    }

    fun saveLanguage(context: Context, language: String) {
        context.getSharedPreferences("nexus_ui_prefs", Context.MODE_PRIVATE)
            .edit().putString("language", language).apply()
    }
}
