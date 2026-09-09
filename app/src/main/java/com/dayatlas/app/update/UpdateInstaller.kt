package com.dayatlas.app.update

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dayatlas.app.R
import com.dayatlas.app.RecentsHider
import java.io.File

/**
 * Downloads an update APK via the system [DownloadManager] (so progress shows
 * as a normal download notification - no custom progress UI to maintain),
 * then hands the finished file to the system installer.
 */
object UpdateInstaller {
    private const val FILE_NAME = "DayAtlas-update.apk"
    private const val CHANNEL_ID = "dayatlas_update"
    private const val NOTIF_ID_INSTALL = 43
    private const val NOTIF_ID_PERMISSION = 44

    /**
     * [silent] suppresses toast/dialog prompts. When the activity is gone
     * (RecentsHider / background SampleService), install is offered via a
     * tap-to-install notification instead of a blocked background startActivity.
     */
    fun download(context: Context, info: UpdateInfo, silent: Boolean = false) {
        val appContext = context.applicationContext
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

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                runCatching { ctx.unregisterReceiver(this) }
                promptInstall(ctx.applicationContext, target, info.version, silent)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (!silent) {
            Toast.makeText(appContext, R.string.update_downloading_toast, Toast.LENGTH_SHORT).show()
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
            } else {
                notifyAction(
                    context,
                    NOTIF_ID_PERMISSION,
                    context.getString(R.string.notif_update_permission_title),
                    context.getString(R.string.notif_update_permission_text),
                    settingsIntent,
                )
            }
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
        RecentsHider.retainForExternalNavigation()
        val started = runCatching { context.startActivity(intent) }.isSuccess
        if (!started) {
            if (!silent) {
                Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show()
            }
            notifyAction(
                context,
                NOTIF_ID_INSTALL,
                context.getString(R.string.notif_update_ready_title),
                context.getString(R.string.notif_update_ready_text, version),
                intent,
            )
        } else if (silent) {
            // Background startActivity often "succeeds" without showing UI on
            // Android 10+. Always leave a tap-to-install notification as backup.
            notifyAction(
                context,
                NOTIF_ID_INSTALL,
                context.getString(R.string.notif_update_ready_title),
                context.getString(R.string.notif_update_ready_text, version),
                intent,
            )
        }
    }

    private fun ensureUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_update_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.notify(id, notification)
    }
}
