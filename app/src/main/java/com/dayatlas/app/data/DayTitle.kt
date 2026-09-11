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

    fun localToday(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        LocalDate.now(zoneId)

    fun iso(date: LocalDate): String = date.toString()

    fun format(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        val month = monthShort(date, locale)
        val prefix = when (locale.language) {
            "tr" -> "Günlük"
            "de" -> "Tag"
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
            else -> if (hours > 0) "$hours h $minutes min" else "$minutes min"
        }
    }

    private fun monthShort(date: LocalDate, locale: Locale): String = when (locale.language) {
        "tr" -> MONTHS_TR[date.monthValue - 1]
        "de" -> MONTHS_DE[date.monthValue - 1]
        else -> date.month.getDisplayName(TextStyle.SHORT, locale)
            .replace(".", "")
            .take(3)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}
