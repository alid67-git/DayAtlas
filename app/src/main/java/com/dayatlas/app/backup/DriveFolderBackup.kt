package com.dayatlas.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.dayatlas.app.data.DayJson
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.prefs.AppPrefs
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Copies each local day's GPX file into a user-picked folder (typically
 * Google Drive via the system folder picker). Drive's own client then syncs
 * that folder to the cloud — no OAuth / Play Services Drive SDK.
 *
 * Each day is backed up exactly once, ever: once a day's GPX file exists in
 * the backup folder — from an auto run or a manual one, whichever happened
 * first — it is never re-uploaded, even if the local copy changes later
 * that day (more points appended, a jump deleted). This is deliberate: a
 * day's backup is a final snapshot, not something that keeps getting
 * overwritten.
 *
 * Auto-run triggers: [BackupScheduler]'s daily 23:00 alarm (independent of
 * app activity), plus opportunistic piggybacks on SampleService and app
 * start that can catch a day earlier in case the alarm is delayed. Manual
 * run available from Settings ("Şimdi yedekle") — same once-only rule
 * applies, so a manual backup does not get re-done by the next auto run.
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

    data class RestoreResult(
        val ok: Boolean,
        val restored: Int = 0,
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
        // A (possibly different) folder was just picked - it needs its own
        // full scan before the "only today" shortcut is safe to use.
        prefs.driveInitialBackupDone = false
    }

    fun clearFolder(context: Context, prefs: AppPrefs) {
        val uri = prefs.driveTreeUri?.let(Uri::parse)
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
        }
        prefs.driveTreeUri = null
        prefs.lastDriveBackupDay = null
        prefs.driveInitialBackupDone = false
    }

    /**
     * If auto-backup is on and we have not yet backed up for [today], run once.
     * Silent — for [BackupCheckReceiver] / SampleService / Application.onCreate.
     * Once a full backup has completed at least once
     * ([AppPrefs.driveInitialBackupDone]), this only needs to check today's
     * file — every earlier day is already up there (or was already skipped
     * as unavailable) and, per the once-only rule, is never revisited.
     */
    fun maybeRunDaily(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        val prefs = AppPrefs(app)
        if (!prefs.driveBackupEnabled || !hasFolder(prefs)) {
            onDone?.invoke()
            return
        }
        val today = DayTitle.iso(DayTitle.localToday())
        if (prefs.lastDriveBackupDay == today) {
            onDone?.invoke()
            return
        }
        val fullScan = !prefs.driveInitialBackupDone
        runAsync(app, prefs, fullScan = fullScan) { onDone?.invoke() }
    }

    /** Manual "Şimdi yedekle": always a full scan, so a jump deleted on an
     * older day (or a file added by [restoreNow]) gets picked up too. */
    fun runNow(
        context: Context,
        onDone: (Result) -> Unit,
    ) {
        val app = context.applicationContext
        val prefs = AppPrefs(app)
        runAsync(app, prefs, fullScan = true, onDone = onDone)
    }

    private fun runAsync(
        context: Context,
        prefs: AppPrefs,
        fullScan: Boolean,
        onDone: ((Result) -> Unit)? = null,
    ) {
        if (!running.compareAndSet(false, true)) {
            main.post { onDone?.invoke(Result(false, message = "busy")) }
            return
        }
        io.execute {
            val result = runCatching { backupLocked(context, prefs, fullScan) }
                .getOrElse { e ->
                    Log.w(TAG, "backup failed", e)
                    Result(false, message = e.message ?: "error")
                }
            if (result.ok) {
                prefs.lastDriveBackupDay = DayTitle.iso(DayTitle.localToday())
                if (fullScan) prefs.driveInitialBackupDone = true
            }
            running.set(false)
            if (onDone != null) {
                main.post { onDone(result) }
            } else if (!result.ok) {
                Log.w(TAG, "daily backup: ${result.message}")
            }
        }
    }

    private fun backupLocked(context: Context, prefs: AppPrefs, fullScan: Boolean): Result {
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
        val todayIso = DayTitle.iso(DayTitle.localToday())
        // Refresh derived GPX from JSON before upload.
        DayStore(context).let { store ->
            if (fullScan) store.ensureGpxForAllDays()
            else store.ensureGpx(todayIso)
        }
        val files = daysDir.listFiles()
            ?.filter {
                it.isFile && it.name.endsWith(".gpx") &&
                    (fullScan || it.name.startsWith(todayIso))
            }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.isEmpty()) {
            return Result(true, uploaded = 0)
        }

        var count = 0
        for (file in files) {
            // Once a day's file exists in the backup folder, it is final —
            // never re-uploaded, no matter how the local copy changes
            // afterward. See the once-only rule in the class doc comment.
            if (tree.findFile(file.name) != null) continue

            upsert(context, tree, file.name, "application/gpx+xml", file.readBytes())
            count++
        }
        return Result(true, uploaded = count)
    }

    /**
     * Reads every `.gpx` day file found in the selected Drive folder — the
     * backup format — and rebuilds the matching local `files/days/*.json`
     * from it, overwriting any local file for that date. For a fresh
     * install / new phone where local storage is empty, the backup folder
     * is treated as the source of truth. JSON stays the local live-storage
     * format either way; only the on-disk backup is GPX.
     */
    fun restoreNow(
        context: Context,
        onDone: (RestoreResult) -> Unit,
    ) {
        val app = context.applicationContext
        val prefs = AppPrefs(app)
        if (!running.compareAndSet(false, true)) {
            main.post { onDone(RestoreResult(false, message = "busy")) }
            return
        }
        io.execute {
            val result = runCatching { restoreLocked(app, prefs) }
                .getOrElse { e ->
                    Log.w(TAG, "restore failed", e)
                    RestoreResult(false, message = e.message ?: "error")
                }
            running.set(false)
            main.post { onDone(result) }
        }
    }

    private fun restoreLocked(context: Context, prefs: AppPrefs): RestoreResult {
        val uriStr = prefs.driveTreeUri
            ?: return RestoreResult(false, message = "no_folder")
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            ?: return RestoreResult(false, message = "invalid_folder")
        if (!tree.canRead()) {
            return RestoreResult(false, message = "not_readable")
        }

        val daysDir = File(context.filesDir, "days").also { it.mkdirs() }
        val gpxFiles = tree.listFiles().filter { it.isFile && it.name?.endsWith(".gpx") == true }
        if (gpxFiles.isEmpty()) {
            return RestoreResult(true, restored = 0)
        }

        var count = 0
        for (doc in gpxFiles) {
            val name = doc.name ?: continue
            val dateIso = name.removeSuffix(".gpx")
            val bytes = context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() }
                ?: continue
            val record = runCatching { DayJson.fromGpx(dateIso, String(bytes, Charsets.UTF_8)) }
                .getOrNull() ?: continue
            DayJson.writeAtomic(File(daysDir, "$dateIso.json"), DayJson.toJson(record))
            count++
        }
        return RestoreResult(true, restored = count)
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
