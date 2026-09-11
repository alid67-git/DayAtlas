package com.dayatlas.app.prefs

import android.content.Context
import android.content.SharedPreferences
import com.dayatlas.app.AppLocale

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

    /**
     * Alarm delay currently in force. Starts at [intervalSeconds] and may
     * coarsen while sitting still; never finer than the user-configured base.
     */
    var effectiveIntervalSeconds: Int
        get() {
            val base = intervalSeconds
            if (!prefs.contains(EFFECTIVE_INTERVAL_SECONDS)) return base
            val raw = prefs.getInt(EFFECTIVE_INTERVAL_SECONDS, base)
            val clamped =
                if (raw in ALLOWED_INTERVAL_SECONDS) raw else base
            return clamped.coerceAtLeast(base)
        }
        set(value) {
            val base = intervalSeconds
            val clamped =
                if (value in ALLOWED_INTERVAL_SECONDS) value else base
            prefs.edit()
                .putInt(EFFECTIVE_INTERVAL_SECONDS, clamped.coerceAtLeast(base))
                .apply()
        }

    val effectiveIntervalMillis: Long
        get() = effectiveIntervalSeconds * 1_000L

    var stationaryStreak: Int
        get() = prefs.getInt(STATIONARY_STREAK, 0).coerceAtLeast(0)
        set(value) = prefs.edit().putInt(STATIONARY_STREAK, value.coerceAtLeast(0)).apply()

    var lastSampleLat: Double?
        get() =
            if (prefs.contains(LAST_SAMPLE_LAT)) {
                Double.fromBits(prefs.getLong(LAST_SAMPLE_LAT, 0L))
            } else {
                null
            }
        set(value) {
            if (value == null) {
                prefs.edit().remove(LAST_SAMPLE_LAT).apply()
            } else {
                prefs.edit().putLong(LAST_SAMPLE_LAT, value.toRawBits()).apply()
            }
        }

    var lastSampleLon: Double?
        get() =
            if (prefs.contains(LAST_SAMPLE_LON)) {
                Double.fromBits(prefs.getLong(LAST_SAMPLE_LON, 0L))
            } else {
                null
            }
        set(value) {
            if (value == null) {
                prefs.edit().remove(LAST_SAMPLE_LON).apply()
            } else {
                prefs.edit().putLong(LAST_SAMPLE_LON, value.toRawBits()).apply()
            }
        }

    /** Clear adaptive backoff (e.g. settings interval change or tracking start). */
    fun resetStationaryBackoff() {
        prefs.edit()
            .putInt(EFFECTIVE_INTERVAL_SECONDS, intervalSeconds)
            .putInt(STATIONARY_STREAK, 0)
            .remove(LAST_SAMPLE_LAT)
            .remove(LAST_SAMPLE_LON)
            .apply()
    }

    /** versionName of the build whose "what's new" dialog has already been shown. */
    var lastSeenBuildNoteVersion: String?
        get() = prefs.getString(LAST_SEEN_BUILD_NOTE, null)
        set(value) = prefs.edit().putString(LAST_SEEN_BUILD_NOTE, value).apply()

    /** epoch millis of the last background (SampleService-driven) update check. */
    var lastUpdateCheckMillis: Long
        get() = prefs.getLong(LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(LAST_UPDATE_CHECK, value).apply()

    /** DownloadManager id for an in-flight update APK, or -1 when none / finished. */
    var pendingUpdateDownloadId: Long
        get() = prefs.getLong(PENDING_UPDATE_DOWNLOAD_ID, -1L)
        set(value) = prefs.edit().putLong(PENDING_UPDATE_DOWNLOAD_ID, value).apply()

    /** version label (e.g. "v0.6.6") for the pending update APK. */
    var pendingUpdateVersion: String?
        get() = prefs.getString(PENDING_UPDATE_VERSION, null)
        set(value) = prefs.edit().putString(PENDING_UPDATE_VERSION, value).apply()

    /** Whether the pending download was started without UI prompts. */
    var pendingUpdateSilent: Boolean
        get() = prefs.getBoolean(PENDING_UPDATE_SILENT, true)
        set(value) = prefs.edit().putBoolean(PENDING_UPDATE_SILENT, value).apply()

    fun clearPendingUpdate() {
        prefs.edit()
            .remove(PENDING_UPDATE_DOWNLOAD_ID)
            .remove(PENDING_UPDATE_VERSION)
            .remove(PENDING_UPDATE_SILENT)
            .apply()
    }

    /** Daily folder backup (SAF / Google Drive picker) master switch. */
    var driveBackupEnabled: Boolean
        get() = prefs.getBoolean(DRIVE_BACKUP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(DRIVE_BACKUP_ENABLED, value).apply()

    /** content:// tree URI from ACTION_OPEN_DOCUMENT_TREE. */
    var driveTreeUri: String?
        get() = prefs.getString(DRIVE_TREE_URI, null)
        set(value) = prefs.edit().putString(DRIVE_TREE_URI, value).apply()

    /** Local calendar day (yyyy-MM-dd) of the last successful auto/manual backup run. */
    var lastDriveBackupDay: String?
        get() = prefs.getString(LAST_DRIVE_BACKUP_DAY, null)
        set(value) = prefs.edit().putString(LAST_DRIVE_BACKUP_DAY, value).apply()

    /** True once a full (all local day files) Drive backup has completed at least
     * once - after that, the daily auto-run only needs to touch today's file. */
    var driveInitialBackupDone: Boolean
        get() = prefs.getBoolean(DRIVE_INITIAL_BACKUP_DONE, false)
        set(value) = prefs.edit().putBoolean(DRIVE_INITIAL_BACKUP_DONE, value).apply()

    /** Comma-joined [com.dayatlas.app.route.DayStatKind] keys, drag-reorder order. */
    var dayStatsOrderRaw: String?
        get() = prefs.getString(DAY_STATS_ORDER, null)
        set(value) = prefs.edit().putString(DAY_STATS_ORDER, value).apply()

    /** [com.dayatlas.app.route.DayStatKind] keys hidden from the day-stats row. */
    var dayStatsHidden: Set<String>
        get() = prefs.getStringSet(DAY_STATS_HIDDEN, emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet(DAY_STATS_HIDDEN, value).apply()

    /**
     * App UI language: empty = follow system; otherwise `tr` / `en` / `de`.
     * Applied via [com.dayatlas.app.AppLocale].
     */
    var appLanguage: String
        get() = AppLocale.normalize(prefs.getString(APP_LANGUAGE, AppLocale.SYSTEM))
        set(value) = prefs.edit().putString(APP_LANGUAGE, AppLocale.normalize(value)).apply()

    companion object {
        /** Default / recommended: 1 minute — denser track than the old 5 min. */
        const val DEFAULT_INTERVAL_SECONDS = 60
        val ALLOWED_INTERVAL_SECONDS = intArrayOf(30, 60, 180, 300)

        private const val PREFS = "dayatlas_prefs"
        private const val DAILY_MODE = "daily_mode"
        private const val TRACKING = "tracking_enabled"
        private const val INTERVAL_SECONDS = "interval_seconds"
        private const val INTERVAL_MINUTES_LEGACY = "interval_minutes"
        private const val EFFECTIVE_INTERVAL_SECONDS = "effective_interval_seconds"
        private const val STATIONARY_STREAK = "stationary_streak"
        private const val LAST_SAMPLE_LAT = "last_sample_lat_bits"
        private const val LAST_SAMPLE_LON = "last_sample_lon_bits"
        private const val LAST_SEEN_BUILD_NOTE = "last_seen_build_note_version"
        private const val LAST_UPDATE_CHECK = "last_update_check_millis"
        private const val PENDING_UPDATE_DOWNLOAD_ID = "pending_update_download_id"
        private const val PENDING_UPDATE_VERSION = "pending_update_version"
        private const val PENDING_UPDATE_SILENT = "pending_update_silent"
        private const val DRIVE_BACKUP_ENABLED = "drive_backup_enabled"
        private const val DRIVE_TREE_URI = "drive_tree_uri"
        private const val LAST_DRIVE_BACKUP_DAY = "last_drive_backup_day"
        private const val DRIVE_INITIAL_BACKUP_DONE = "drive_initial_backup_done"
        private const val DAY_STATS_ORDER = "day_stats_order"
        private const val DAY_STATS_HIDDEN = "day_stats_hidden"
        private const val APP_LANGUAGE = "app_language"
    }
}
