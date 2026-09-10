package com.dayatlas.app.update

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dayatlas.app.R
import com.dayatlas.app.RecentsHider
import com.dayatlas.app.prefs.AppPrefs
import java.io.File

/**
 * Downloads an update APK via the system [DownloadManager], then hands the
 * finished file to the system installer.
 *
 * Completion paths (belt and suspenders):
 * 1. [DownloadCompleteReceiver] — manifest-exported for the system broadcast
 * 2. [watchDownload] — polls DownloadManager while this process is alive
 * 3. [resumePending] — on next UI open / process start if OEM dropped (1)
 */
object UpdateInstaller {
    private const val FILE_NAME = "DayAtlas-update.apk"
    private const val CHANNEL_ID = "dayatlas_update"
    private const val NOTIF_ID_INSTALL = 43
    private const val NOTIF_ID_PERMISSION = 44
    private const val POLL_INTERVAL_MS = 2_000L
    private const val POLL_MAX_MS = 15 * 60_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Avoid re-launching the system installer on every MainActivity resume. */
    @Volatile
    private var offeredInstallUiThisProcess = false

    @Volatile
    private var watchRunnable: Runnable? = null

    /**
     * [silent] suppresses toast prompts only. Install UI is always attempted
     * (startActivity + tap-to-install notification) — background restrictions
     * make startActivity unreliable, so the notification is the reliable path.
     */
    fun download(context: Context, info: UpdateInfo, silent: Boolean = false) {
        val appContext = context.applicationContext
        val prefs = AppPrefs(appContext)

        // Avoid stacking downloads when SampleService and MainActivity both
        // discover the same release.
        if (prefs.pendingUpdateVersion == info.version) {
            val existingId = prefs.pendingUpdateDownloadId
            if (existingId >= 0L && isDownloadActive(appContext, existingId)) {
                watchDownload(appContext, existingId)
                resumePending(appContext, offerUi = !silent)
                return
            }
            val ready = apkFile(appContext)
            if (ready.exists() && ready.length() >= 1024) {
                promptInstall(appContext, ready, info.version, silent)
                return
            }
        }

        cancelPendingDownload(appContext)
        val dir = appContext.getExternalFilesDir("apk")?.apply { mkdirs() }
        val target = File(dir, FILE_NAME)
        if (target.exists()) target.delete()

        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle(appContext.getString(R.string.update_downloading_title, info.version))
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationUri(Uri.fromFile(target))
            .setMimeType("application/vnd.android.package-archive")

        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)
        prefs.pendingUpdateDownloadId = downloadId
        prefs.pendingUpdateVersion = info.version
        prefs.pendingUpdateSilent = silent

        watchDownload(appContext, downloadId)

        if (!silent) {
            Toast.makeText(appContext, R.string.update_downloading_toast, Toast.LENGTH_SHORT).show()
        }
    }

    /** Called from [DownloadCompleteReceiver] when DownloadManager finishes. */
    fun onDownloadComplete(context: Context, downloadId: Long) {
        val appContext = context.applicationContext
        val prefs = AppPrefs(appContext)
        if (downloadId < 0L) return
        // Accept our pending id, or an orphan complete if version is known.
        if (prefs.pendingUpdateDownloadId >= 0L && downloadId != prefs.pendingUpdateDownloadId) {
            return
        }
        finishDownload(appContext, downloadId)
    }

    /**
     * Re-offer install when the user returns to the app. Also recovers an APK
     * left behind by older builds that downloaded but never installed.
     */
    fun resumePending(context: Context, offerUi: Boolean = true) {
        val appContext = context.applicationContext
        val prefs = AppPrefs(appContext)
        val file = apkFile(appContext)
        var version = prefs.pendingUpdateVersion
        val downloadId = prefs.pendingUpdateDownloadId

        if (downloadId >= 0L) {
            when {
                isDownloadSuccessful(appContext, downloadId) -> {
                    prefs.pendingUpdateDownloadId = -1L
                    stopWatch()
                }
                isDownloadActive(appContext, downloadId) -> {
                    watchDownload(appContext, downloadId)
                    return
                }
                isDownloadFailed(appContext, downloadId) -> {
                    prefs.clearPendingUpdate()
                    stopWatch()
                    return
                }
                else -> {
                    prefs.pendingUpdateDownloadId = -1L
                }
            }
        }

        // Orphan APK from a previous broken installer (no prefs / process died).
        if (version == null && file.exists() && file.length() >= 1024) {
            version = "indirilen"
            prefs.pendingUpdateVersion = version
            prefs.pendingUpdateSilent = true
        }

        if (version == null) return
        if (!file.exists() || file.length() < 1024) {
            if (downloadId < 0L) prefs.clearPendingUpdate()
            return
        }
        val launchUi = offerUi && !offeredInstallUiThisProcess
        if (launchUi) offeredInstallUiThisProcess = true
        // Always attempt the installer when the user opened the app.
        promptInstall(appContext, file, version, silent = !launchUi)
    }

    private fun finishDownload(context: Context, downloadId: Long) {
        stopWatch()
        val prefs = AppPrefs(context)
        val version = prefs.pendingUpdateVersion ?: return
        val silent = prefs.pendingUpdateSilent
        val file = apkFile(context)
        if (!isDownloadSuccessful(context, downloadId) || !file.exists() || file.length() < 1024) {
            // Brief grace: file may not be flushed yet when the broadcast fires.
            mainHandler.postDelayed({
                if (!file.exists() || file.length() < 1024) {
                    if (isDownloadFailed(context, downloadId)) {
                        prefs.clearPendingUpdate()
                        if (!silent) {
                            Toast.makeText(
                                context,
                                R.string.update_download_failed,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                    return@postDelayed
                }
                prefs.pendingUpdateDownloadId = -1L
                promptInstall(context, file, version, silent)
            }, 1_000L)
            return
        }
        prefs.pendingUpdateDownloadId = -1L
        promptInstall(context, file, version, silent)
    }

    private fun watchDownload(context: Context, downloadId: Long) {
        stopWatch()
        val appContext = context.applicationContext
        val startedAt = System.currentTimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                val prefs = AppPrefs(appContext)
                if (prefs.pendingUpdateDownloadId != downloadId) {
                    watchRunnable = null
                    return
                }
                when {
                    isDownloadSuccessful(appContext, downloadId) -> {
                        watchRunnable = null
                        finishDownload(appContext, downloadId)
                    }
                    isDownloadFailed(appContext, downloadId) -> {
                        watchRunnable = null
                        prefs.clearPendingUpdate()
                    }
                    System.currentTimeMillis() - startedAt > POLL_MAX_MS -> {
                        watchRunnable = null
                    }
                    else -> mainHandler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }
        watchRunnable = runnable
        mainHandler.postDelayed(runnable, POLL_INTERVAL_MS)
    }

    private fun stopWatch() {
        watchRunnable?.let { mainHandler.removeCallbacks(it) }
        watchRunnable = null
    }

    private fun apkFile(context: Context): File =
        File(context.getExternalFilesDir("apk"), FILE_NAME)

    private fun cancelPendingDownload(context: Context) {
        stopWatch()
        val prefs = AppPrefs(context)
        val id = prefs.pendingUpdateDownloadId
        if (id >= 0L) {
            runCatching {
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
            }
        }
        prefs.clearPendingUpdate()
    }

    private fun isDownloadSuccessful(context: Context, id: Long): Boolean =
        downloadStatus(context, id) == DownloadManager.STATUS_SUCCESSFUL

    private fun isDownloadFailed(context: Context, id: Long): Boolean =
        downloadStatus(context, id) == DownloadManager.STATUS_FAILED

    private fun isDownloadActive(context: Context, id: Long): Boolean {
        val status = downloadStatus(context, id) ?: return false
        return status == DownloadManager.STATUS_PENDING ||
            status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PAUSED
    }

    private fun downloadStatus(context: Context, id: Long): Int? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor: Cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val idx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (idx < 0) return null
            return it.getInt(idx)
        }
    }

    private fun promptInstall(
        context: Context,
        file: File,
        version: String,
        silent: Boolean,
    ) {
        if (!file.exists() || file.length() < 1024) {
            if (!silent) {
                Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
            }
            return
        }
        ensureUpdateChannel(context)

        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (!silent) {
                Toast.makeText(
                    context,
                    R.string.update_install_permission_needed,
                    Toast.LENGTH_LONG,
                ).show()
                RecentsHider.retainForExternalNavigation()
                runCatching { context.startActivity(settingsIntent) }
            }
            notifyAction(
                context,
                NOTIF_ID_PERMISSION,
                context.getString(R.string.notif_update_permission_title),
                context.getString(R.string.notif_update_permission_text),
                settingsIntent,
            )
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, file.name, uri)
        }

        // High-priority notification is the reliable path on Android 10+.
        notifyAction(
            context,
            NOTIF_ID_INSTALL,
            context.getString(R.string.notif_update_ready_title),
            context.getString(R.string.notif_update_ready_text, version),
            intent,
        )

        // Always try to open the installer. From a BroadcastReceiver / poll
        // callback this often works; from pure background it may no-op — the
        // notification above covers that case.
        RecentsHider.retainForExternalNavigation()
        val started = runCatching { context.startActivity(intent) }.isSuccess
        if (!started && !silent) {
            Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_update_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = context.getString(R.string.notif_update_ready_title)
        nm.createNotificationChannel(channel)
    }

    private fun notifyAction(
        context: Context,
        id: Int,
        title: String,
        text: String,
        action: Intent,
    ) {
        // MUTABLE: install intent carries a FileProvider URI grant; IMMUTABLE
        // PendingIntents drop that grant on several OEMs when tapped.
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val pending = PendingIntent.getActivity(context, id, action, piFlags)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dot)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.notify(id, notification)
    }
}
