package com.dayatlas.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dayatlas.app.BuildConfig
import com.dayatlas.app.prefs.AppPrefs

/**
 * Fires at local midday (~12:00). Runs a silent update check and always
 * reschedules the next midday alarm.
 */
class UpdateCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext
        UpdateCheckScheduler.scheduleNext(app)
        val prefs = AppPrefs(app)
        if (!UpdateCheckRunner.shouldCheckToday(prefs)) {
            pending.finish()
            return
        }
        UpdateChecker.check(BuildConfig.VERSION_NAME) { info ->
            UpdateCheckRunner.markChecked(AppPrefs(app))
            if (info != null) {
                UpdateInstaller.download(app, info, silent = true)
            }
            pending.finish()
        }
    }
}
