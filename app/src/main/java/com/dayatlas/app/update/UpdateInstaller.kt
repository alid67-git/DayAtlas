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
 * Completion is handled by [DownloadCompleteReceiver] (manifest-exported so
 * the system broadcast is delivered even after RecentsHider kills the UI
 * process). [resumePending] re-checks on the next UI open in case the
 * broadcast was dropped by an OEM.
 */
object UpdateInstaller {
    private const val FILE_NAME = "DayAtlas-update.apk"
    private const val CHANNEL_ID = "dayatlas_update"
    private const val NOTIF_ID_INSTALL = 43
    private const val NOTIF_ID_PERMISSION = 44

    /** Avoid re-launching the system installer on every MainActivity resume. */
    @Volatile
    private var offeredInstallUiThisProcess = false

    /**
     * [silent] suppresses toast prompts and prefers a tap-to-install
     * notification when startActivity would be blocked in the background.
     */
    fun download(context: Context, info: UpdateInfo, silent: Boolean = false) {
        val appContext = context.applicationContext
        val prefs = AppPrefs(appContext)

        // Avoid stacking downloads / orphan receivers when SampleService and
        // MainActivity both discover the same release.
        if (prefs.pendingUpdateVersion == info.version) {
            val existingId = prefs.pendingUpdateDownloadId
            if (existingId >= 0L && isDownloadActive(appContext, existingId)) {
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
        // Remember silent vs interactive so the manifest receiver can behave
        // the same way after a process restart.
        prefs.pendingUpdateSilent = silent

        if (!silent) {
            Toast.makeText(appContext, R.string.update_downloading_toast, Toast.LENGTH_SHORT).show()
        }
    }

    /** Called from [DownloadCompleteReceiver] when DownloadManager finishes. */
    fun onDownloadComplete(context: Context, downloadId: Long) {
        val appContext = context.applicationContext
        val prefs = AppPrefs(appContext)
        if (downloadId < 0L || downloadId != prefs.pendingUpdateDownloadId) return
        val version = prefs.pendingUpdateVersion ?: return
        val silent = prefs.pendingUpdateSilent
        val file = apkFile(appContext)
        if (!isDownloadSuccessful(appContext, downloadId) || !file.exists() || file.length() < 1024) {
            prefs.clearPendingUpdate()
            if (!silent) {
                Toast.makeText(appContext, R.string.update_download_failed, Toast.LENGTH_LONG).show()
            }
            return
        }
        // Keep version + file; clear id so we don't treat this as still downloading.
        prefs.pendingUpdateDownloadId = -1L
        promptInstall(appContext, file, version, silent)
    }

    /**
     * Re-offer install when the user returns to the app. [offerUi] true opens
     * the system installer once per process; later calls only refresh the
     * tap-to-install notification.
     */
    fun resumePending(context: Context, offerUi: Boolean = true) {
        val appContext = context.applicationContext
        val prefs = AppPrefs(appContext)
        val version = prefs.pendingUpdateVersion ?: return
        val downloadId = prefs.pendingUpdateDownloadId
        val file = apkFile(appContext)

        if (downloadId >= 0L) {
            when {
                isDownloadSuccessful(appContext, downloadId) -> {
                    prefs.pendingUpdateDownloadId = -1L
                }
                isDownloadActive(appContext, downloadId) -> return
                isDownloadFailed(appContext, downloadId) -> {
                    prefs.clearPendingUpdate()
                    return
                }
                else -> {
                    // Unknown / purged from DownloadManager history — fall through to file check.
                    prefs.pendingUpdateDownloadId = -1L
                }
            }
        }

        if (!file.exists() || file.length() < 1024) {
            if (downloadId < 0L) prefs.clearPendingUpdate()
            return
        }
        val launchUi = offerUi && !offeredInstallUiThisProcess
        if (launchUi) offeredInstallUiThisProcess = true
        promptInstall(appContext, file, version, silent = !launchUi)
    }

    private fun apkFile(context: Context): File =
        File(context.getExternalFilesDir("apk"), FILE_NAME)

    private fun cancelPendingDownload(context: Context) {
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

        // Always leave a high-priority tap-to-install notification: background
        // startActivity is unreliable on Android 10+ and RecentsHider may have
        // already finished the task.
        notifyAction(
            context,
            NOTIF_ID_INSTALL,
            context.getString(R.string.notif_update_ready_title),
            context.getString(R.string.notif_update_ready_text, version),
            intent,
        )

        if (!silent) {
            RecentsHider.retainForExternalNavigation()
            val started = runCatching { context.startActivity(intent) }.isSuccess
            if (!started) {
                Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun ensureUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_update_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    private fun notifyAction(
        context: Context,
        id: Int,
        title: String,
        text: String,
        action: Intent,
    ) {
        val pending = PendingIntent.getActivity(
            context,
            id,
            action,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dot)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.notify(id, notification)
    }
}
