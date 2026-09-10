package com.dayatlas.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Manifest-exported so [DownloadManager.ACTION_DOWNLOAD_COMPLETE] from the
 * system is delivered even when no activity/service holds a dynamic receiver.
 * Dynamic RECEIVER_NOT_EXPORTED registration previously dropped this broadcast
 * on Android 13+.
 */
class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        UpdateInstaller.onDownloadComplete(context, id)
    }
}
