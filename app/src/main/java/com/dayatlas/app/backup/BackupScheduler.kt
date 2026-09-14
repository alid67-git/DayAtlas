package com.dayatlas.app.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dayatlas.app.location.PermissionHelper
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Once-daily Drive folder backup at local 23:00, independent of GPS
 * sampling or the app being opened - mirrors [com.dayatlas.app.update.UpdateCheckScheduler].
 * Without this, a day with restricted background execution (or the app
 * simply never opened) would never get backed up at all, since the only
 * other triggers ([DriveFolderBackup.maybeRunDaily] piggybacked on
 * [com.dayatlas.app.location.SampleService] and app start) depend on the
 * app actually running that day.
 */
object BackupScheduler {
    private const val REQ_BACKUP = 1102
    val BACKUP_TIME: LocalTime = LocalTime.of(23, 0)

    fun ensureScheduled(context: Context, zoneId: ZoneId = ZoneId.systemDefault()) {
        scheduleNext(context, zoneId)
    }

    fun scheduleNext(context: Context, zoneId: ZoneId = ZoneId.systemDefault()) {
        val app = context.applicationContext
        val now = ZonedDateTime.now(zoneId)
        var next = now.toLocalDate().atTime(BACKUP_TIME).atZone(zoneId)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        setWakeup(app, next.toInstant().toEpochMilli(), backupIntent(app))
    }

    private fun backupIntent(context: Context): PendingIntent {
        val intent = Intent(context, BackupCheckReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQ_BACKUP,
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
