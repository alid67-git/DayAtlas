package com.dayatlas.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.dayatlas.app.prefs.AppPrefs

/** Applies the stored app language via AppCompat per-app locales. */
object AppLocale {
    const val SYSTEM = ""
    const val TR = "tr"
    const val EN = "en"
    const val DE = "de"

    fun applyFromPrefs(prefs: AppPrefs) {
        apply(prefs.appLanguage)
    }

    fun apply(tag: String) {
        val locales = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun normalize(tag: String?): String = when (tag) {
        TR, EN, DE -> tag
        else -> SYSTEM
    }
}
