package com.dayatlas.app.location

import android.content.Context
import com.dayatlas.app.prefs.AppPrefs

object TrackingController {
    fun onAppStart(context: Context, prefs: AppPrefs = AppPrefs(context)) {
        if (prefs.dailyMode) {
            prefs.trackingEnabled = true
        }
        if (prefs.trackingEnabled) {
            SampleScheduler.ensureScheduled(context, prefs)
        }
    }

    fun start(context: Context, prefs: AppPrefs = AppPrefs(context), sampleSoon: Boolean = true) {
        prefs.trackingEnabled = true
        prefs.resetStationaryBackoff()
        if (sampleSoon) {
            SampleScheduler.scheduleNext(context, prefs, delayMs = 3_000L)
        } else {
            SampleScheduler.ensureScheduled(context, prefs)
        }
    }

    fun stop(context: Context, prefs: AppPrefs = AppPrefs(context)) {
        prefs.trackingEnabled = false
        prefs.resetStationaryBackoff()
        SampleScheduler.cancel(context)
    }

    fun setDailyMode(context: Context, enabled: Boolean, prefs: AppPrefs = AppPrefs(context)) {
        prefs.dailyMode = enabled
        if (enabled) {
            start(context, prefs, sampleSoon = true)
        } else {
            stop(context, prefs)
        }
    }

    fun shouldSample(context: Context, prefs: AppPrefs = AppPrefs(context)): Boolean {
        if (!prefs.trackingEnabled && !prefs.dailyMode) return false
        return PermissionHelper.hasLocation(context)
    }
}
