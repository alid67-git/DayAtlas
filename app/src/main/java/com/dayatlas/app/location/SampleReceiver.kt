package com.dayatlas.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
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
        val service = Intent(context, SampleService::class.java)
        ContextCompat.startForegroundService(context, service)
    }
}
