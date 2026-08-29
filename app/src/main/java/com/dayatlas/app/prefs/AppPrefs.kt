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

    var intervalMinutes: Int
        get() {
            val raw = prefs.getInt(INTERVAL, DEFAULT_INTERVAL_MINUTES)
            return if (raw in ALLOWED_INTERVALS) raw else DEFAULT_INTERVAL_MINUTES
        }
        set(value) {
            val clamped = if (value in ALLOWED_INTERVALS) value else DEFAULT_INTERVAL_MINUTES
            prefs.edit().putInt(INTERVAL, clamped).apply()
        }

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 5
        val ALLOWED_INTERVALS = intArrayOf(3, 4, 5)

        private const val PREFS = "dayatlas_prefs"
        private const val DAILY_MODE = "daily_mode"
        private const val TRACKING = "tracking_enabled"
        private const val INTERVAL = "interval_minutes"
    }
}
