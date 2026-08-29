package com.dayatlas.app.data

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object DayTitle {
    private val MONTHS_TR = arrayOf(
        "Oca", "Şub", "Mar", "Nis", "May", "Haz",
        "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara",
    )

    fun localToday(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        LocalDate.now(zoneId)

    fun iso(date: LocalDate): String = date.toString()

    fun format(date: LocalDate, locale: Locale = Locale("tr", "TR")): String {
        if (locale.language == "tr") {
            return "Günlük ${date.dayOfMonth} ${MONTHS_TR[date.monthValue - 1]} ${date.year}"
        }
        return "Daily ${date.dayOfMonth} ${date.month.name.lowercase(locale).replaceFirstChar { it.titlecase(locale) }.take(3)} ${date.year}"
    }

    fun formatDistance(meters: Double, locale: Locale = Locale("tr", "TR")): String {
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            String.format(locale, "%.1f km", meters / 1000.0)
        }
    }
}
