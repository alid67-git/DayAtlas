package com.dayatlas.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.dayatlas.app.R
import java.io.File

/**
 * Chooser for adding a day photo: take with the system camera or pick from
 * the gallery. Camera capture writes into `cacheDir/camera` via FileProvider
 * (see `res/xml/file_paths.xml`); callers then pass that URI to [PhotoStore.addPhoto].
 */
object DayPhotoAdd {
    fun showSourceChooser(
        activity: AppCompatActivity,
        onCamera: () -> Unit,
        onGallery: () -> Unit,
    ) {
        val labels = ArrayList<String>(2)
        val actions = ArrayList<() -> Unit>(2)
        if (canTakePicture(activity)) {
            labels.add(activity.getString(R.string.day_photo_camera))
            actions.add(onCamera)
        }
        labels.add(activity.getString(R.string.day_photo_gallery))
        actions.add(onGallery)
        if (labels.size == 1) {
            actions[0].invoke()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.day_photo_add)
            .setItems(labels.toTypedArray()) { _, which -> actions[which].invoke() }
            .setNegativeButton(R.string.export_cancel, null)
            .show()
    }

    /**
     * Creates an empty JPEG under cache and a content URI the camera app can
     * write into. Delete the file after [PhotoStore] finishes (or on cancel).
     */
    fun createCaptureTarget(context: Context): CaptureTarget {
        val app = context.applicationContext
        val dir = File(app.cacheDir, "camera").also { it.mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        val uri = FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            file,
        )
        return CaptureTarget(file = file, uri = uri)
    }

    fun canTakePicture(context: Context): Boolean {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return false
        val probe = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        return probe.resolveActivity(pm) != null
    }

    data class CaptureTarget(
        val file: File,
        val uri: Uri,
    )
}
