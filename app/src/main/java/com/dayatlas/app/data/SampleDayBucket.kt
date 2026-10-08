package com.dayatlas.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Decides which local calendar day a GPS sample belongs to.
 *
 * DayAtlas days follow the **phone wall clock** ([System.currentTimeMillis]
 * + [ZoneId.systemDefault]), not the GPS fix's own timestamp. GPS time can
 * sit slightly ahead of the wall clock near midnight (or after a timezone
 * change), which used to file an evening sample under the next day.
 *
 * The stored point time still prefers the GPS clock when it falls on the
 * same local day as the wall clock; otherwise the wall time is used so the
 * point and the day file stay consistent.
 */
object SampleDayBucket {
    data class Resolution(
        val localDate: LocalDate,
        val pointTimeMillis: Long,
        val usedWallClockForPoint: Boolean,
    )

    fun resolve(
        gpsTimeMillis: Long,
        wallTimeMillis: Long,
        zoneId: ZoneId,
    ): Resolution {
        val wallDate = Instant.ofEpochMilli(wallTimeMillis).atZone(zoneId).toLocalDate()
        val gpsMillis = gpsTimeMillis.takeIf { it > 0L } ?: wallTimeMillis
        val gpsDate = Instant.ofEpochMilli(gpsMillis).atZone(zoneId).toLocalDate()
        return if (gpsDate == wallDate) {
            Resolution(
                localDate = wallDate,
                pointTimeMillis = gpsMillis,
                usedWallClockForPoint = gpsMillis == wallTimeMillis && gpsTimeMillis <= 0L,
            )
        } else {
            Resolution(
                localDate = wallDate,
                pointTimeMillis = wallTimeMillis,
                usedWallClockForPoint = true,
            )
        }
    }
}
