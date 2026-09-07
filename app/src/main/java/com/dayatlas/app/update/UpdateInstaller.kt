package com.dayatlas.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dayatlas.app.R
import java.io.File

/**
 * Downloads an update APK via the system [DownloadManager] (so progress shows
 * as a normal download notification - no custom progress UI to maintain),
 * then hands the finished file to the system installer.
 */
object UpdateInstaller {
    private const val FILE_NAME = "DayAtlas-update.apk"

    fun download(context: Context, info: UpdateInfo) {
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
                promptInstall(ctx, target)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        Toast.makeText(appContext, R.string.update_downloading_toast, Toast.LENGTH_SHORT).show()
    }

    private fun promptInstall(context: Context, file: File) {
        if (!file.exists() || file.length() < 1024) {
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
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
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show()
            }
    }
}
