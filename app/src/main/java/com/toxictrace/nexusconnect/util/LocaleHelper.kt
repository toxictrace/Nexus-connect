package com.toxictrace.nexusconnect.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    /**
     * Returns the real system locale — before any app overrides.
     * Uses Resources.getSystem() which always reflects the OS locale.
     */
    private fun systemLocale(): Locale {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.content.res.Resources.getSystem()
                .configuration.locales.get(0) ?: Locale.ENGLISH
        } else {
            @Suppress("DEPRECATION")
            android.content.res.Resources.getSystem().configuration.locale
        }
    }

    /**
     * Resolves the target locale:
     * - "en"     → English
     * - "ru"     → Russian
     * - "system" → Russian if system locale is ru-*, otherwise English
     */
    fun resolveLocale(language: String): Locale {
        return when (language) {
            "en" -> Locale.ENGLISH
            "ru" -> Locale("ru")
            else -> {
                // "system" — follow OS but only between EN and RU
                val sys = systemLocale()
                if (sys.language == "ru") Locale("ru") else Locale.ENGLISH
            }
        }
    }

    fun applyLocale(context: Context, language: String): Context {
        val locale = resolveLocale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
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
