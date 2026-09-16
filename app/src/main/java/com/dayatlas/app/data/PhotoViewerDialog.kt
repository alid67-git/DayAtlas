package com.dayatlas.app.data

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dayatlas.app.R
import com.dayatlas.app.databinding.DialogPhotoViewerBinding

/** Shows one day's photo full-size, with a delete option. */
object PhotoViewerDialog {
    fun show(
        activity: AppCompatActivity,
        store: DayStore,
        photoStore: PhotoStore,
        dateIso: String,
        photoName: String,
        onChanged: () -> Unit,
    ) {
        val binding = DialogPhotoViewerBinding.inflate(LayoutInflater.from(activity))
        val full = photoStore.fullFile(dateIso, photoName)
        // Falls back to the thumbnail if the full copy was already dropped
        // after a confirmed Drive backup - see PhotoStore.dropFullCopiesAfterBackup.
        val source = if (full.exists()) full else photoStore.thumbFile(dateIso, photoName)
        val bitmap = runCatching { BitmapFactory.decodeFile(source.absolutePath) }.getOrNull()
        binding.photoViewerImage.setImageBitmap(bitmap)

        AlertDialog.Builder(activity)
            .setView(binding.root)
            .setNegativeButton(R.string.export_cancel, null)
            .setPositiveButton(R.string.day_photo_delete) { _, _ ->
                photoStore.deletePhoto(dateIso, photoName, store)
                onChanged()
            }
            .show()
    }
}
