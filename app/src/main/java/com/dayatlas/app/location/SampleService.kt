package com.dayatlas.app.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.dayatlas.app.BuildConfig
import com.dayatlas.app.R
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.prefs.AppPrefs
import com.dayatlas.app.update.UpdateChecker
import com.dayatlas.app.update.UpdateInstaller
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SampleService : Service() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!busy.compareAndSet(false, true)) {
            return START_NOT_STICKY
        }
        acquireWakeLock()
        val prefs = AppPrefs(this)
        if (!TrackingController.shouldSample(this, prefs)) {
            finish(reschedule = false)
            return START_NOT_STICKY
        }

        // The app may run unattended for days in daily mode with the UI never
        // opened, so MainActivity's launch-time update check may never fire.
        // Piggyback a once-a-day check on this already-scheduled, already
        // wake-locked tick instead of adding a separate alarm/receiver.
        val pending = AtomicInteger(1)
        fun stepDone() {
            if (pending.decrementAndGet() == 0) {
                finish(reschedule = true)
            }
        }

        if (shouldCheckForUpdate(prefs)) {
            pending.incrementAndGet()
            UpdateChecker.check(BuildConfig.VERSION_NAME) { info ->
                // Stamp after the check so a failed/offline attempt can retry
                // on the next sample tick instead of waiting a full day.
                prefs.lastUpdateCheckMillis = System.currentTimeMillis()
                if (info != null) {
                    UpdateInstaller.download(applicationContext, info, silent = true)
                }
                stepDone()
            }
        }

        val lm = getSystemService(LocationManager::class.java)
        LocationSampler.request(lm, io) { location ->
            main.post {
                if (location != null) {
                    StationaryBackoff.recordSample(
                        prefs,
                        location.latitude,
                        location.longitude,
                    )
                    runCatching { DayStore(applicationContext).append(location) }
                    sendBroadcast(Intents.pointSaved(this))
                }
                stepDone()
            }
        }
        return START_NOT_STICKY
    }

    private fun shouldCheckForUpdate(prefs: AppPrefs): Boolean {
        if (BuildConfig.DEBUG) return false
        return System.currentTimeMillis() - prefs.lastUpdateCheckMillis >= UPDATE_CHECK_INTERVAL_MS
    }

    private fun finish(reschedule: Boolean) {
        if (reschedule) {
            val prefs = AppPrefs(this)
            SampleScheduler.scheduleNext(
                this,
                prefs,
                delayMs = prefs.effectiveIntervalMillis,
            )
        }
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        busy.set(false)
        stopSelf()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dot)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_sampling))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dayatlas:sample").apply {
            setReferenceCounted(false)
            acquire(LocationSampler.TIMEOUT_MS + 5_000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "dayatlas_sample"
        private const val NOTIF_ID = 42
        private const val UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
