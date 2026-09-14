package com.dayatlas.app.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires at local 23:00. Runs the daily Drive backup and always
 * reschedules the next day's alarm. */
class BackupCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext
        BackupScheduler.scheduleNext(app)
        DriveFolderBackup.maybeRunDaily(app) { pending.finish() }
    }
}
