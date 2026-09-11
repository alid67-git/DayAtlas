package com.dayatlas.app.route

import com.dayatlas.app.R

/**
 * One tile in the draggable/hideable day-stats grid on the Daily tab.
 * All kinds share one 3-column grid with a uniform white card background.
 */
enum class DayStatKind(val key: String, val labelRes: Int) {
    DISTANCE("distance", R.string.map_day_distance),
    LAST_POINT("last_point", R.string.last_point),
    POINT_COUNT("point_count", R.string.point_count),
    GPS_INTERVAL("gps_interval", R.string.stat_gps_interval),
    MAX_SPEED("max_speed", R.string.stat_max_speed),
    AVG_SPEED("avg_speed", R.string.stat_avg_speed),
    ACTIVE_DURATION("active_duration", R.string.stat_active_duration),
    ;

    companion object {
        val DEFAULT_ORDER = entries.toList()

        fun fromKey(key: String): DayStatKind? = entries.firstOrNull { it.key == key }

        /** Parses a stored comma-joined key list, appending any kind missing from it
         * (new app version added a stat the saved order predates). */
        fun parseOrder(raw: String?): List<DayStatKind> {
            val parsed = raw?.split(",")?.mapNotNull { fromKey(it) }.orEmpty()
            val missing = DEFAULT_ORDER.filter { it !in parsed }
            return parsed + missing
        }

        fun joinOrder(order: List<DayStatKind>): String = order.joinToString(",") { it.key }
    }
}
