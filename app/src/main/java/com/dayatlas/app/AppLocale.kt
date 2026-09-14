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
    const val ZH = "zh"
    const val HI = "hi"
    const val ES = "es"
    const val FR = "fr"
    const val AR = "ar"

    /** All languages the UI and help page can switch to, in menu order. */
    val SUPPORTED = listOf(TR, EN, DE, ZH, HI, ES, FR, AR)

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

    fun normalize(tag: String?): String = if (tag != null && SUPPORTED.contains(tag)) tag else SYSTEM
}
