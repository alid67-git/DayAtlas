package com.dayatlas.app.prefs

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var dailyMode: Boolean
        get() = prefs.getBoolean(DAILY_MODE, false)
        set(value) = prefs.edit().putBoolean(DAILY_MODE, value).apply()

    var trackingEnabled: Boolean
        get() = prefs.getBoolean(TRACKING, false)
        set(value) = prefs.edit().putBoolean(TRACKING, value).apply()

    /**
     * Sampling period in seconds: 30 / 60 / 180 / 300.
     * Migrates the old `interval_minutes` (3/4/5) key on first read.
     */
    var intervalSeconds: Int
        get() {
            if (prefs.contains(INTERVAL_SECONDS)) {
                val raw = prefs.getInt(INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS)
                return if (raw in ALLOWED_INTERVAL_SECONDS) raw else DEFAULT_INTERVAL_SECONDS
            }
            if (prefs.contains(INTERVAL_MINUTES_LEGACY)) {
                val legacy = prefs.getInt(INTERVAL_MINUTES_LEGACY, 5)
                val migrated = when (legacy) {
                    3 -> 180
                    4 -> 180
                    5 -> 300
                    else -> DEFAULT_INTERVAL_SECONDS
                }
                prefs.edit()
                    .putInt(INTERVAL_SECONDS, migrated)
                    .remove(INTERVAL_MINUTES_LEGACY)
                    .apply()
                return migrated
            }
            return DEFAULT_INTERVAL_SECONDS
        }
        set(value) {
            val clamped =
                if (value in ALLOWED_INTERVAL_SECONDS) value else DEFAULT_INTERVAL_SECONDS
            prefs.edit()
                .putInt(INTERVAL_SECONDS, clamped)
                .remove(INTERVAL_MINUTES_LEGACY)
                .apply()
        }

    /** @deprecated Use [intervalSeconds]; kept for call-site clarity in ms math. */
    val intervalMillis: Long
        get() = intervalSeconds * 1_000L

    /** versionName of the build whose "what's new" dialog has already been shown. */
    var lastSeenBuildNoteVersion: String?
        get() = prefs.getString(LAST_SEEN_BUILD_NOTE, null)
        set(value) = prefs.edit().putString(LAST_SEEN_BUILD_NOTE, value).apply()

    /** epoch millis of the last background (SampleService-driven) update check. */
    var lastUpdateCheckMillis: Long
        get() = prefs.getLong(LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(LAST_UPDATE_CHECK, value).apply()

    companion object {
        /** Default / recommended: 1 minute — denser track than the old 5 min. */
        const val DEFAULT_INTERVAL_SECONDS = 60
        val ALLOWED_INTERVAL_SECONDS = intArrayOf(30, 60, 180, 300)

        private const val PREFS = "dayatlas_prefs"
        private const val DAILY_MODE = "daily_mode"
        private const val TRACKING = "tracking_enabled"
        private const val INTERVAL_SECONDS = "interval_seconds"
        private const val INTERVAL_MINUTES_LEGACY = "interval_minutes"
        private const val LAST_SEEN_BUILD_NOTE = "last_seen_build_note_version"
        private const val LAST_UPDATE_CHECK = "last_update_check_millis"
    }
}
