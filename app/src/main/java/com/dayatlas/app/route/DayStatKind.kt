package com.dayatlas.app.route

import com.dayatlas.app.R

/**
 * One tile in the draggable day-stats grid on the Daily tab.
 * Each kind has a soft tinted card and a matching accent icon.
 */
enum class DayStatKind(
    val key: String,
    val labelRes: Int,
    val iconRes: Int,
    val iconTintRes: Int,
    val cardBackgroundRes: Int,
) {
    DISTANCE(
        "distance",
        R.string.map_day_distance,
        R.drawable.ic_stat_distance,
        R.color.stat_distance_icon,
        R.drawable.bg_stat_distance,
    ),
    LAST_POINT(
        "last_point",
        R.string.last_point,
        R.drawable.ic_stat_clock,
        R.color.stat_last_point_icon,
        R.drawable.bg_stat_last_point,
    ),
    POINT_COUNT(
        "point_count",
        R.string.point_count,
        R.drawable.ic_stat_nodes,
        R.color.stat_point_count_icon,
        R.drawable.bg_stat_point_count,
    ),
    GPS_INTERVAL(
        "gps_interval",
        R.string.stat_gps_interval,
        R.drawable.ic_stat_satellite,
        R.color.stat_gps_icon,
        R.drawable.bg_stat_gps,
    ),
    MAX_SPEED(
        "max_speed",
        R.string.stat_max_speed,
        R.drawable.ic_stat_speed,
        R.color.stat_max_speed_icon,
        R.drawable.bg_speed_max,
    ),
    AVG_SPEED(
        "avg_speed",
        R.string.stat_avg_speed,
        R.drawable.ic_stat_chart,
        R.color.stat_avg_speed_icon,
        R.drawable.bg_speed_avg,
    ),
    ACTIVE_DURATION(
        "active_duration",
        R.string.stat_active_duration,
        R.drawable.ic_stat_timer,
        R.color.stat_active_icon,
        R.drawable.bg_speed_active,
    ),
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
