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
 * interval has coarsened (say, to 5 min), the *next* GPS check only
 * happens after that whole interval elapses, and StationaryBackoff then
 * needs [StationaryBackoff.MOVEMENT_STREAK_TO_RESET] consecutive off-anchor
 * fixes before speeding back up again — so real movement right after a
 * coarse tick could go unnoticed for up to two of that long interval's
 * worth of time before recording resumes at full resolution.
 *
 * `TYPE_SIGNIFICANT_MOTION` is a purpose-built, very-low-power hardware
 * trigger for exactly "has the device moved" — Android's own docs describe
 * well under one firing per minute as the expected rate, so it already
 * filters out the kind of small jostling (picked up, pocket shift) that a
 * hand-rolled raw-accelerometer threshold would need its own debounce
 * logic to ignore. It fires once, must be re-armed after every firing, and
 * — like the `*AllowWhileIdle` `AlarmManager` calls already used elsewhere
 * in this app — is allowed to wake the device out of Doze.
 *
 * When it fires, this kicks an out-of-schedule GPS sample immediately
 * instead of waiting for the next scheduled tick. The sensor is only ever
 * a hint to check *sooner*: [StationaryBackoff]'s own tolerance circle,
 * evaluated on that sample's real fix, still decides whether this was
 * genuine movement or a false alarm — a false alarm just costs one extra
 * GPS check, the coarse interval continues unchanged. Devices without this
 * sensor (all methods below become no-ops) fall back to the timer alone,
 * exactly as before this existed.
 */
object MotionWakeTrigger {
    private const val TAG = "MotionWakeTrigger"

    private val listener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            val context = appContext ?: return
            // The sensor deactivates itself on every firing - re-arm first
            // so a burst of activity keeps getting caught, not just its
            // first moment.
            register(context)
            val prefs = AppPrefs(context)
            if (!TrackingController.shouldSample(context, prefs)) return
            // Out-of-schedule check: do not push the regular alarm back —
            // only start the service (with FGS-failure retry).
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
