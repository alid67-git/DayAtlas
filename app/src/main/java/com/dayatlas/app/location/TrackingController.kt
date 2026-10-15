package com.dayatlas.app.location

import android.content.Context
import android.os.Build
import com.dayatlas.app.prefs.AppPrefs

object TrackingController {
    /** First sample soon after boot / process start so a day is not empty
     * while waiting a full adaptive interval. */
    private const val BOOT_SAMPLE_DELAY_MS = 8_000L

    fun onAppStart(context: Context, prefs: AppPrefs = AppPrefs(context)) {
        if (prefs.dailyMode) {
            prefs.trackingEnabled = true
        }
        if (prefs.trackingEnabled) {
            // Prefer a near-term tick; ensureScheduled alone used the full
            // interval and left empty mornings after reboot.
            SampleScheduler.scheduleNext(context, prefs, delayMs = BOOT_SAMPLE_DELAY_MS)
            SampleScheduler.scheduleMidnight(context)
            MotionWakeTrigger.register(context)
        }
    }

    /**
     * @param resetBackoff When null (default), backoff resets only if tracking
     * was previously off. Pass true/false to force. Daily-mode app opens must
     * not wipe a mid-ladder effective interval.
     */
    fun start(
        context: Context,
        prefs: AppPrefs = AppPrefs(context),
        sampleSoon: Boolean = true,
        resetBackoff: Boolean? = null,
    ) {
        val wasOff = !prefs.trackingEnabled
        prefs.trackingEnabled = true
        if (resetBackoff ?: wasOff) {
            prefs.resetStationaryBackoff()
        }
        if (sampleSoon) {
            SampleScheduler.scheduleNext(context, prefs, delayMs = 3_000L)
        } else {
            SampleScheduler.ensureScheduled(context, prefs)
        }
        MotionWakeTrigger.register(context)
    }

    fun stop(context: Context, prefs: AppPrefs = AppPrefs(context)) {
        prefs.trackingEnabled = false
        prefs.resetStationaryBackoff()
        SampleScheduler.cancel(context)
        MotionWakeTrigger.unregister(context)
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
        if (!PermissionHelper.hasLocation(context)) return false
        // Alarm-driven ticks run in the background. On Android 10+ (and
        // especially 14+ location FGS) while-in-use alone cannot start the
        // sample service — ACCESS_BACKGROUND_LOCATION is required.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !PermissionHelper.hasBackgroundLocation(context) &&
            !PermissionHelper.isAppForeground(context)
        ) {
            return false
        }
        return true
    }

    /** Why an unattended sample would be skipped right now, or null if OK. */
    fun sampleBlockReason(context: Context, prefs: AppPrefs = AppPrefs(context)): String? {
        if (!prefs.trackingEnabled && !prefs.dailyMode) return "tracking_off"
        if (!PermissionHelper.hasLocation(context)) return "no_location"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !PermissionHelper.hasBackgroundLocation(context)
        ) {
            return "no_bg_location"
        }
        if (!PermissionHelper.canScheduleExactAlarms(context)) return "no_exact_alarm"
        return null
    }
}
