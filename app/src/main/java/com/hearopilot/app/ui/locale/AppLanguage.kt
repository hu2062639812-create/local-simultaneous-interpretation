package com.hearopilot.app.ui.locale

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val storageKey: String) {
    SYSTEM("system"),
    SIMPLIFIED_CHINESE("zh"),
    ENGLISH("en");

    companion object {
        fun fromStorageKey(key: String?): AppLanguage =
            entries.firstOrNull { it.storageKey == key } ?: SYSTEM
    }
}

/**
 * Persists the interface language separately from DataStore so it can be read
 * synchronously in [android.content.ContextWrapper.attachBaseContext].
 */
object AppLocale {
    private const val PREFS_NAME = "interface_language"
    private const val KEY_LANGUAGE = "language"

    fun current(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLanguage.fromStorageKey(prefs.getString(KEY_LANGUAGE, null))
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.storageKey)
            .apply()
    }

    fun wrap(base: Context): Context {
        val systemLocale = Resources.getSystem().configuration.locales[0]
        val locale = when (current(base)) {
            AppLanguage.SYSTEM -> systemLocale
            AppLanguage.SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
            AppLanguage.ENGLISH -> Locale.ENGLISH
        }
        Locale.setDefault(locale)
        if (locale == systemLocale) return base

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
