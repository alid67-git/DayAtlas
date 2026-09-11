package com.dayatlas.app.stats

import java.time.LocalDate

/** Preset windows for the Statistics tab range chips. */
enum class StatsRange {
    TODAY,
    LAST_7,
    LAST_30,
    THIS_MONTH,
    ALL,
    ;

    fun bounds(today: LocalDate, earliest: LocalDate?): Pair<LocalDate, LocalDate>? {
        return when (this) {
            TODAY -> today to today
            LAST_7 -> today.minusDays(6) to today
            LAST_30 -> today.minusDays(29) to today
            THIS_MONTH -> today.withDayOfMonth(1) to today
            ALL -> {
                val start = earliest ?: return null
                start to today
            }
        }
    }
}
