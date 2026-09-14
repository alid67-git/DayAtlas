package com.dayatlas.app.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

object DayTitle {
    private val MONTHS_TR = arrayOf(
        "Oca", "Şub", "Mar", "Nis", "May", "Haz",
        "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara",
    )
    private val MONTHS_DE = arrayOf(
        "Jan", "Feb", "Mär", "Apr", "Mai", "Jun",
        "Jul", "Aug", "Sep", "Okt", "Nov", "Dez",
    )
    private val MONTHS_ES = arrayOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic",
    )
    private val MONTHS_FR = arrayOf(
        "Jan", "Fév", "Mar", "Avr", "Mai", "Jun",
        "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc",
    )
    private val MONTHS_ZH = arrayOf(
        "1月", "2月", "3月", "4月", "5月", "6月",
        "7月", "8月", "9月", "10月", "11月", "12月",
    )
    private val MONTHS_HI = arrayOf(
        "जन", "फ़र", "मार्च", "अप्रैल", "मई", "जून",
        "जुल", "अग", "सित", "अक्तू", "नव", "दिस",
    )
    private val MONTHS_AR = arrayOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    fun localToday(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        LocalDate.now(zoneId)

    fun iso(date: LocalDate): String = date.toString()

    fun format(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        val month = monthShort(date, locale)
        val prefix = when (locale.language) {
            "tr" -> "Günlük"
            "de" -> "Tag"
            "zh" -> "每日"
            "hi" -> "दैनिक"
            "es" -> "Diario"
            "fr" -> "Journalier"
            "ar" -> "يومي"
            else -> "Daily"
        }
        return "$prefix ${date.dayOfMonth} $month ${date.year}"
    }

    /** Compact day label for stats bars, e.g. "11 Eyl" / "11 Sep". */
    fun formatShort(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        "${date.dayOfMonth} ${monthShort(date, locale)}"

    fun formatDistance(meters: Double, locale: Locale = Locale.getDefault()): String {
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            String.format(locale, "%.1f km", meters / 1000.0)
        }
    }

    fun formatSpeed(kmh: Double, locale: Locale = Locale.getDefault()): String {
        val unit = if (locale.language == "tr") "km/sa" else "km/h"
        return String.format(locale, "%.0f %s", kmh, unit)
    }

    fun formatDuration(millis: Long, locale: Locale = Locale.getDefault()): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when (locale.language) {
            "tr" -> if (hours > 0) "$hours sa $minutes dk" else "$minutes dk"
            "de" -> if (hours > 0) "$hours Std $minutes Min" else "$minutes Min"
            "zh" -> if (hours > 0) "$hours 时 $minutes 分" else "$minutes 分"
            "hi" -> if (hours > 0) "$hours घं $minutes मि" else "$minutes मि"
            "ar" -> if (hours > 0) "$hours س $minutes د" else "$minutes د"
            else -> if (hours > 0) "$hours h $minutes min" else "$minutes min"
        }
    }

    private fun monthShort(date: LocalDate, locale: Locale): String = when (locale.language) {
        "tr" -> MONTHS_TR[date.monthValue - 1]
        "de" -> MONTHS_DE[date.monthValue - 1]
        "es" -> MONTHS_ES[date.monthValue - 1]
        "fr" -> MONTHS_FR[date.monthValue - 1]
        "zh" -> MONTHS_ZH[date.monthValue - 1]
        "hi" -> MONTHS_HI[date.monthValue - 1]
        "ar" -> MONTHS_AR[date.monthValue - 1]
        else -> date.month.getDisplayName(TextStyle.SHORT, locale)
            .replace(".", "")
            .take(3)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}
