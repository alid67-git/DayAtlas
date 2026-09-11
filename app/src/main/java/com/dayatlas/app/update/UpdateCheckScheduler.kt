package com.dayatlas.app.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dayatlas.app.BuildConfig
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.location.PermissionHelper
import com.dayatlas.app.prefs.AppPrefs
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Once-daily software update check at local midday (12:00). Independent of
 * GPS sampling — stays scheduled even when tracking is off.
 */
object UpdateCheckScheduler {
    private const val REQ_MIDDAY = 1101
    val MIDDAY: LocalTime = LocalTime.of(12, 0)

    fun ensureScheduled(context: Context, zoneId: ZoneId = ZoneId.systemDefault()) {
        scheduleNext(context, zoneId)
    }

    fun scheduleNext(context: Context, zoneId: ZoneId = ZoneId.systemDefault()) {
        val app = context.applicationContext
        val now = ZonedDateTime.now(zoneId)
        var next = now.toLocalDate().atTime(MIDDAY).atZone(zoneId)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        setWakeup(app, next.toInstant().toEpochMilli(), middayIntent(app))
    }

    private fun middayIntent(context: Context): PendingIntent {
        val intent = Intent(context, UpdateCheckReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQ_MIDDAY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setWakeup(context: Context, triggerAtMillis: Long, pending: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (PermissionHelper.canScheduleExactAlarms(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }
}

/** Shared gate + silent check for midday alarm, SampleService, and launch catch-up. */
object UpdateCheckRunner {
    fun shouldCheckToday(prefs: AppPrefs): Boolean {
        if (BuildConfig.DEBUG) return false
        val today = DayTitle.iso(DayTitle.localToday())
        return prefs.lastUpdateCheckDay != today
    }

    /** Catch-up when midday was missed and local time is already past noon. */
    fun shouldCatchUp(prefs: AppPrefs, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!shouldCheckToday(prefs)) return false
        return !LocalTime.now(zoneId).isBefore(UpdateCheckScheduler.MIDDAY)
    }

    fun markChecked(prefs: AppPrefs) {
        prefs.lastUpdateCheckDay = DayTitle.iso(DayTitle.localToday())
        prefs.lastUpdateCheckMillis = System.currentTimeMillis()
    }

    fun maybeCheckAndDownload(
        context: Context,
        prefs: AppPrefs = AppPrefs(context),
        requirePastMidday: Boolean = false,
    ) {
        val ok = if (requirePastMidday) shouldCatchUp(prefs) else shouldCheckToday(prefs)
        if (!ok) return
        val app = context.applicationContext
        UpdateChecker.check(BuildConfig.VERSION_NAME) { info ->
            markChecked(AppPrefs(app))
            if (info != null) {
                UpdateInstaller.download(app, info, silent = true)
            }
        }
    }
}
