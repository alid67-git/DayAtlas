package com.dayatlas.app.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.util.Log
import com.dayatlas.app.prefs.AppPrefs

/**
 * Shortens the "just started moving after sitting still" latency that
 * [StationaryBackoff]'s pure timer otherwise has. Once the effective
 * interval has coarsened (say, to 5 min), waiting for the next alarm alone
 * can leave real movement unnoticed for minutes.
 *
 * `TYPE_SIGNIFICANT_MOTION` is a purpose-built, very-low-power hardware
 * trigger for exactly "has the device moved". It fires once, must be
 * re-armed after every firing, and — like the `*AllowWhileIdle`
 * `AlarmManager` calls already used elsewhere — may wake the device out
 * of Doze.
 *
 * When it fires while the interval is coarsened, we:
 * 1. Tip [StationaryBackoff] back to the user base rate (keep the anchor),
 * 2. Re-arm the next alarm in a few seconds (not 5 minutes),
 * 3. Kick an out-of-schedule GPS sample immediately.
 *
 * The GPS fix still decides lasting movement vs a false jostle; a false
 * alarm only costs a short fine-grained burst before coarsening climbs
 * again. Devices without this sensor fall back to the timer + first
 * off-circle tip in [StationaryBackoff.onSample].
 */
object MotionWakeTrigger {
    private const val TAG = "MotionWakeTrigger"

    /** How soon to schedule the follow-up sample after a motion hint. */
    const val FOLLOW_UP_DELAY_MS = 3_000L

    private val listener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            val context = appContext ?: return
            // The sensor deactivates itself on every firing - re-arm first
            // so a burst of activity keeps getting caught, not just its
            // first moment.
            register(context)
            val prefs = AppPrefs(context)
            if (!TrackingController.shouldSample(context, prefs)) return
            StationaryBackoff.speedUpForSuspectedMotion(prefs)
            SampleScheduler.scheduleNext(context, prefs, delayMs = FOLLOW_UP_DELAY_MS)
            SampleStarter.startService(context, prefs)
        }
    }

    @Volatile
    private var appContext: Context? = null

    /** Idempotent — safe to call whenever tracking might be starting or resuming. */
    fun register(context: Context) {
        val app = context.applicationContext
        appContext = app
        val sensor = significantMotionSensor(app) ?: return
        val sensorManager = app.getSystemService(SensorManager::class.java) ?: return
        // Clear any stale registration first - requestTriggerSensor on an
        // already-armed listener is a no-op that would otherwise mask a
        // genuine re-arm failure.
        runCatching { sensorManager.cancelTriggerSensor(listener, sensor) }
        val armed = runCatching { sensorManager.requestTriggerSensor(listener, sensor) }
            .getOrDefault(false)
        if (!armed) {
            Log.w(TAG, "requestTriggerSensor failed - falling back to the timer alone")
        }
    }

    fun unregister(context: Context) {
        val app = context.applicationContext
        val sensor = significantMotionSensor(app) ?: return
        val sensorManager = app.getSystemService(SensorManager::class.java) ?: return
        runCatching { sensorManager.cancelTriggerSensor(listener, sensor) }
    }

    private fun significantMotionSensor(context: Context): Sensor? {
        val sensorManager = context.getSystemService(SensorManager::class.java) ?: return null
        return sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    }
}
