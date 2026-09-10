package com.dayatlas.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.prefs.AppPrefs
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Copies local `files/days/*` into a user-picked folder (typically Google Drive
 * via the system folder picker). Drive's own client then syncs that folder to
 * the cloud — no OAuth / Play Services Drive SDK.
 *
 * Daily auto-run: once per local calendar day (piggybacked on SampleService /
 * app start). Manual run available from Settings.
 */
object DriveFolderBackup {
    private const val TAG = "DriveFolderBackup"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)

    data class Result(
        val ok: Boolean,
        val uploaded: Int = 0,
        val message: String? = null,
    )

    fun hasFolder(prefs: AppPrefs): Boolean = !prefs.driveTreeUri.isNullOrBlank()

    fun folderSummary(context: Context, prefs: AppPrefs): String? {
        val uri = prefs.driveTreeUri?.let(Uri::parse) ?: return null
        val tree = DocumentFile.fromTreeUri(context, uri) ?: return uri.toString()
        return tree.name ?: uri.lastPathSegment
    }

    /** Persistable grant after [Intent.ACTION_OPEN_DOCUMENT_TREE]. */
    fun takeFolder(context: Context, prefs: AppPrefs, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
        prefs.driveTreeUri = treeUri.toString()
    }

    fun clearFolder(context: Context, prefs: AppPrefs) {
        val uri = prefs.driveTreeUri?.let(Uri::parse)
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
        }
        prefs.driveTreeUri = null
        prefs.lastDriveBackupDay = null
    }

    /**
     * If auto-backup is on and we have not yet backed up for [today], run once.
     * Silent — for alarms / SampleService / Application.onCreate.
     */
    fun maybeRunDaily(context: Context) {
        val app = context.applicationContext
        val prefs = AppPrefs(app)
        if (!prefs.driveBackupEnabled || !hasFolder(prefs)) return
        val today = DayTitle.iso(DayTitle.localToday())
        if (prefs.lastDriveBackupDay == today) return
        runAsync(app, prefs) { /* silent */ }
    }

    fun runNow(
        context: Context,
        onDone: (Result) -> Unit,
    ) {
        val app = context.applicationContext
        val prefs = AppPrefs(app)
        runAsync(app, prefs, onDone = onDone)
    }

    private fun runAsync(
        context: Context,
        prefs: AppPrefs,
        onDone: ((Result) -> Unit)? = null,
    ) {
        if (!running.compareAndSet(false, true)) {
            main.post { onDone?.invoke(Result(false, message = "busy")) }
            return
        }
        io.execute {
            val result = runCatching { backupLocked(context, prefs) }
                .getOrElse { e ->
                    Log.w(TAG, "backup failed", e)
                    Result(false, message = e.message ?: "error")
                }
            if (result.ok) {
                prefs.lastDriveBackupDay = DayTitle.iso(DayTitle.localToday())
            }
            running.set(false)
            if (onDone != null) {
                main.post { onDone(result) }
            } else if (!result.ok) {
                Log.w(TAG, "daily backup: ${result.message}")
            }
        }
    }

    private fun backupLocked(context: Context, prefs: AppPrefs): Result {
        val uriStr = prefs.driveTreeUri
            ?: return Result(false, message = "no_folder")
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            ?: return Result(false, message = "invalid_folder")
        if (!tree.canWrite()) {
            return Result(false, message = "not_writable")
        }

        val daysDir = File(context.filesDir, "days")
        if (!daysDir.isDirectory) {
            return Result(true, uploaded = 0)
        }
        val files = daysDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".gpx")) }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.isEmpty()) {
            return Result(true, uploaded = 0)
        }

        var count = 0
        for (file in files) {
            val mime = when {
                file.name.endsWith(".json") -> "application/json"
                file.name.endsWith(".gpx") -> "application/gpx+xml"
                else -> "application/octet-stream"
            }
            upsert(context, tree, file.name, mime, file.readBytes())
            count++
        }
        return Result(true, uploaded = count)
    }

    private fun upsert(
        context: Context,
        tree: DocumentFile,
        fileName: String,
        mime: String,
        bytes: ByteArray,
    ) {
        tree.findFile(fileName)?.delete()
        val displayName = fileName.substringBeforeLast('.', fileName)
        val created = tree.createFile(mime, displayName)
            ?: error("createFile failed: $fileName")
        // Some providers append the extension; rename not available — write bytes.
        val target = tree.findFile(fileName) ?: created
        context.contentResolver.openOutputStream(target.uri, "w")?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: error("openOutputStream failed: $fileName")
    }
}
