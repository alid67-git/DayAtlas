package com.dayatlas.app.location

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

object LocationSampler {
    const val TIMEOUT_MS = 25_000L

    fun request(
        locationManager: LocationManager,
        executor: Executor,
        onResult: (Location?) -> Unit,
    ) {
        val provider = bestProvider(locationManager)
        if (provider == null) {
            onResult(null)
            return
        }
        val done = AtomicBoolean(false)
        val cancel = CancellationSignal()
        val timeoutHandler = Handler(Looper.getMainLooper())
        val finish: (Location?) -> Unit = { loc ->
            if (done.compareAndSet(false, true)) {
                runCatching { cancel.cancel() }
                timeoutHandler.removeCallbacksAndMessages(null)
                onResult(loc)
            }
        }
        timeoutHandler.postDelayed({ finish(null) }, TIMEOUT_MS)
        try {
            locationManager.getCurrentLocation(provider, cancel, executor, finish)
        } catch (_: SecurityException) {
            finish(null)
        } catch (_: IllegalArgumentException) {
            finish(null)
        }
    }

    fun bestProvider(locationManager: LocationManager): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)
        ) {
            return LocationManager.FUSED_PROVIDER
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER
        }
        return locationManager.getProviders(true).firstOrNull()
    }
}
