package com.dayatlas.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors

/**
 * Up to [MAX_PHOTOS_PER_DAY] photos per day, each downsized on the way in so
 * neither on-device storage nor the eventual Drive backup grow unbounded
 * from full-resolution camera photos. Two sizes are kept per photo: a
 * ~1280px "full" copy (for viewing in-app and for the Drive backup) and a
 * ~320px thumbnail (for the day's photo strip). Once a day's full copies are
 * confirmed uploaded to the Drive backup folder, [dropFullCopiesAfterBackup]
 * deletes the local full copies and keeps only the thumbnail - the full
 * quality copy still exists in the backup folder itself, this just keeps
 * the device's own storage from growing forever for photos nobody revisits.
 */
class PhotoStore(context: Context) {
    private val appContext = context.applicationContext

    fun photosDir(dateIso: String): File =
        File(appContext.filesDir, "photos/$dateIso").also { it.mkdirs() }

    fun fullFile(dateIso: String, name: String): File = File(photosDir(dateIso), name)

    fun thumbFile(dateIso: String, name: String): File = File(photosDir(dateIso), thumbName(name))

    private fun thumbName(name: String) = name.substringBeforeLast('.', name) + "_thumb.jpg"

    /**
     * Reads and downsizes [sourceUri] and appends it to [dateIso]'s photos.
     * Does file + bitmap work, so call this off the main thread. Returns
     * false without writing anything if the day is already at the cap or
     * the source couldn't be decoded (not an image, corrupt, etc).
     */
    fun addPhoto(dateIso: String, sourceUri: Uri, store: DayStore): Boolean {
        val existing = store.load(dateIso)?.photos.orEmpty()
        if (existing.size >= MAX_PHOTOS_PER_DAY) return false
        val full = decodeSampled(sourceUri, FULL_MAX_DIMENSION) ?: return false
        val name = "${System.currentTimeMillis()}.jpg"
        writeJpeg(full, fullFile(dateIso, name), FULL_QUALITY)
        val thumb = scaledDownTo(full, THUMB_MAX_DIMENSION)
        writeJpeg(thumb, thumbFile(dateIso, name), THUMB_QUALITY)
        if (thumb !== full) thumb.recycle()
        full.recycle()
        store.setPhotos(dateIso, existing + name)
        return true
    }

    /** [addPhoto] off the [io] executor, delivering the result back on the main thread. */
    fun addPhotoAsync(dateIso: String, sourceUri: Uri, store: DayStore, onDone: (Boolean) -> Unit) {
        io.execute {
            val ok = runCatching { addPhoto(dateIso, sourceUri, store) }.getOrDefault(false)
            main.post { onDone(ok) }
        }
    }

    fun deletePhoto(dateIso: String, name: String, store: DayStore) {
        fullFile(dateIso, name).delete()
        thumbFile(dateIso, name).delete()
        val remaining = store.load(dateIso)?.photos.orEmpty() - name
        store.setPhotos(dateIso, remaining)
    }

    /** Called once [names] are confirmed present in the Drive backup folder - see DriveFolderBackup. */
    fun dropFullCopiesAfterBackup(dateIso: String, names: List<String>) {
        names.forEach { fullFile(dateIso, it).delete() }
    }

    /**
     * Rebuilds the thumbnail from the full copy if it's missing - a restore
     * from Drive only brings back the full-size backup file, never a
     * thumbnail (nothing that small was ever uploaded), so the photo strip
     * needs one regenerated locally the first time each restored photo is
     * seen. No-op if the full copy isn't there either (nothing to build from).
     */
    fun ensureThumbnail(dateIso: String, name: String) {
        val thumb = thumbFile(dateIso, name)
        if (thumb.exists()) return
        val full = fullFile(dateIso, name)
        if (!full.exists()) return
        val decoded = BitmapFactory.decodeFile(full.absolutePath) ?: return
        val scaled = scaledDownTo(decoded, THUMB_MAX_DIMENSION)
        writeJpeg(scaled, thumb, THUMB_QUALITY)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
    }

    private fun decodeSampled(uri: Uri, maxDimension: Int): Bitmap? {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension && bounds.outHeight / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val scaled = scaledDownTo(decoded, maxDimension)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /** A bitmap no larger than [maxDimension] on its long side; returns [source] unchanged if already small enough. */
    private fun scaledDownTo(source: Bitmap, maxDimension: Int): Bitmap {
        val longSide = maxOf(source.width, source.height)
        if (longSide <= maxDimension) return source
        val scale = maxDimension.toFloat() / longSide
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun writeJpeg(bitmap: Bitmap, file: File, quality: Int) {
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
    }

    companion object {
        const val MAX_PHOTOS_PER_DAY = 3
        private const val FULL_MAX_DIMENSION = 1280
        private const val THUMB_MAX_DIMENSION = 320
        private const val FULL_QUALITY = 75
        private const val THUMB_QUALITY = 60

        private val io = Executors.newSingleThreadExecutor()
        private val main = Handler(Looper.getMainLooper())
    }
}
