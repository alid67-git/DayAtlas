package com.dayatlas.app.location

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.dayatlas.app.prefs.AppPrefs

/**
 * Starts [SampleService] and keeps the AlarmManager chain alive even when
 * Android blocks a foreground-service start (common under Doze / OEM kills).
 */
object SampleStarter {
    private const val TAG = "SampleStarter"

    /** Retry delay when a foreground service start is rejected. */
    const val FGS_RETRY_MS = 30_000L

    /** Faster follow-up when a GPS fix timed out / returned null. */
    const val NULL_FIX_RETRY_MS = 45_000L

    /**
     * Arm the next sample alarm *before* starting the service so a killed
     * mid-sample process cannot leave tracking with no wakeup scheduled.
     * [SampleService] rewrites that alarm after backoff updates.
     */
    fun armThenStart(context: Context, prefs: AppPrefs = AppPrefs(context)) {
        SampleScheduler.scheduleNext(context, prefs)
        startService(context, prefs)
    }

    fun startService(context: Context, prefs: AppPrefs = AppPrefs(context)) {
        val app = context.applicationContext
        val service = Intent(app, SampleService::class.java)
        try {
            ContextCompat.startForegroundService(app, service)
        } catch (t: Throwable) {
            Log.w(TAG, "startForegroundService failed; retrying via alarm", t)
            SampleScheduler.scheduleNext(app, prefs, delayMs = FGS_RETRY_MS)
        }
    }

    fun nextDelayAfterNullFix(prefs: AppPrefs): Long {
        val capped = prefs.effectiveIntervalMillis.coerceAtMost(NULL_FIX_RETRY_MS)
        return capped.coerceAtLeast(1_000L)
    }
}
