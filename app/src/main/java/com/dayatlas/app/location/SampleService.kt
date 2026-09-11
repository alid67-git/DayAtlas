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
import com.dayatlas.app.backup.DriveFolderBackup
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.prefs.AppPrefs
import com.dayatlas.app.update.UpdateCheckRunner
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

        // Backup: if midday alarm was killed, a sample tick can still check
        // once per calendar day after noon.
        val pending = AtomicInteger(1)
        fun stepDone() {
            if (pending.decrementAndGet() == 0) {
                finish(reschedule = true)
            }
        }

        if (UpdateCheckRunner.shouldCatchUp(prefs)) {
            pending.incrementAndGet()
            UpdateChecker.check(BuildConfig.VERSION_NAME) { info ->
                UpdateCheckRunner.markChecked(AppPrefs(applicationContext))
                if (info != null) {
                    UpdateInstaller.download(applicationContext, info, silent = true)
                }
                stepDone()
            }
        }

        val lm = getSystemService(LocationManager::class.java)
        // Location callback may arrive on the sampler executor; keep all disk
        // work off the main thread so an open Daily UI cannot ANR.
        LocationSampler.request(lm, io) { location ->
            if (location != null) {
                StationaryBackoff.recordSample(
                    prefs,
                    location.latitude,
                    location.longitude,
                )
                val result = runCatching { DayStore(applicationContext).append(location) }.getOrNull()
                if (result != null && result.wrote) {
                    val last = result.record.points.lastOrNull()
                    if (last != null) {
                        // Extras let the UI update without re-reading disk /
                        // rebuilding the whole map on every tick.
                        sendBroadcast(
                            Intents.pointSaved(
                                this,
                                dateIso = result.record.date,
                                pointCount = result.record.points.size,
                                distanceMeters = result.record.distanceMeters,
                                timeMillis = last.timeMillis,
                                lat = last.lat,
                                lon = last.lon,
                                geometryChanged = result.geometryChanged,
                            ),
                        )
                    }
                }
            }
            main.post { stepDone() }
        }
        return START_NOT_STICKY
    }

    private fun finish(reschedule: Boolean) {
        // Once per local day: copy days/* into the user-picked Drive/folder tree.
        // Prefs check is light; heavy I/O is already async inside maybeRunDaily.
        DriveFolderBackup.maybeRunDaily(this)
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
    }
}
