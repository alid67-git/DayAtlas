package com.dayatlas.app.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dayatlas.app.prefs.AppPrefs
import java.time.ZoneId
import java.time.ZonedDateTime

object SampleScheduler {
    private const val REQ_SAMPLE = 1001
    private const val REQ_MIDNIGHT = 1002

    fun ensureScheduled(context: Context, prefs: AppPrefs = AppPrefs(context)) {
        scheduleNext(context, prefs, delayMs = prefs.intervalMillis)
        scheduleMidnight(context)
    }

    fun scheduleNext(
        context: Context,
        prefs: AppPrefs = AppPrefs(context),
        delayMs: Long = prefs.intervalMillis,
    ) {
        val app = context.applicationContext
        val triggerAt = System.currentTimeMillis() + delayMs.coerceAtLeast(1_000L)
        setWakeup(app, triggerAt, sampleIntent(app))
        scheduleMidnight(app)
    }

    fun scheduleMidnight(context: Context, zoneId: ZoneId = ZoneId.systemDefault()) {
        val app = context.applicationContext
        val nextMidnight = ZonedDateTime.now(zoneId)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val delay = (nextMidnight - System.currentTimeMillis()).coerceAtLeast(1_000L)
        setWakeup(app, System.currentTimeMillis() + delay, midnightIntent(app))
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java)
        am.cancel(sampleIntent(app))
        am.cancel(midnightIntent(app))
    }

    private fun sampleIntent(context: Context): PendingIntent {
        val intent = Intent(context, SampleReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQ_SAMPLE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun midnightIntent(context: Context): PendingIntent {
        val intent = Intent(context, SampleReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQ_MIDNIGHT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setWakeup(
        context: Context,
        triggerAtMillis: Long,
        pending: PendingIntent,
    ) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (PermissionHelper.canScheduleExactAlarms(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }
}
