package com.dayatlas.app.location

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dayatlas.app.prefs.AppPrefs

/**
 * When the user grants/revokes [AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED],
 * re-arm the sample chain. Without this, denying exact alarms cancels them and
 * daily mode goes dark until the next manual open.
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (intent?.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            return
        }
        TrackingController.onAppStart(context, AppPrefs(context))
    }
}
