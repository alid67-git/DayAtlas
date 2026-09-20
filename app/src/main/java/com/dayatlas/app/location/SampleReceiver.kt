package com.dayatlas.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dayatlas.app.prefs.AppPrefs

class SampleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = AppPrefs(context)
        if (prefs.dailyMode) {
            prefs.trackingEnabled = true
        }
        if (!TrackingController.shouldSample(context, prefs)) {
            if (prefs.trackingEnabled || prefs.dailyMode) {
                SampleScheduler.scheduleNext(context, prefs)
            }
            return
        }
        // Schedule the *next* tick before starting work. If the process is
        // killed during the GPS wait, AlarmManager still has a wakeup.
        SampleStarter.armThenStart(context, prefs)
    }
}
