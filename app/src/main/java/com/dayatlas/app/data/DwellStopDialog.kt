package com.dayatlas.app.data

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dayatlas.app.R
import com.dayatlas.app.databinding.DialogDwellStopBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Dwell duration + optional note/photo. [onAddPhoto] is invoked when the user
 * wants to attach a picture; the hosting activity runs the camera/gallery
 * flow then calls [DayStore.setDwellPhoto].
 */
object DwellStopDialog {
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    fun show(
        activity: AppCompatActivity,
        store: DayStore,
        photoStore: PhotoStore,
        dateIso: String,
        stop: DwellStops.Stop,
        onAddPhoto: () -> Unit,
        onChanged: () -> Unit,
    ) {
        val binding = DialogDwellStopBinding.inflate(LayoutInflater.from(activity))
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(stop.startMillis).atZone(zone).toLocalTime().format(TIME_FMT)
        val end = Instant.ofEpochMilli(stop.endMillis).atZone(zone).toLocalTime().format(TIME_FMT)
        val duration = DayTitle.formatDuration(stop.durationMillis)
        binding.dwellSummary.text =
            activity.getString(R.string.dwell_stop_summary, start, end, duration)

        val record = store.load(dateIso)
        val currentNote = record?.dwellNotes?.get(stop.noteKey)
        val currentPhoto = record?.dwellPhotos?.get(stop.noteKey)
        binding.dwellNoteText.setText(currentNote.orEmpty())

        if (currentPhoto != null) {
            photoStore.ensureThumbnail(dateIso, currentPhoto)
            val thumb = photoStore.thumbFile(dateIso, currentPhoto)
            val full = photoStore.fullFile(dateIso, currentPhoto)
            val source = when {
                thumb.exists() -> thumb
                full.exists() -> full
                else -> null
            }
            if (source != null) {
                binding.dwellPhotoPreview.visibility = View.VISIBLE
                binding.dwellPhotoPreview.setImageBitmap(
                    runCatching { BitmapFactory.decodeFile(source.absolutePath) }.getOrNull(),
                )
            }
            binding.dwellPhotoButton.setText(R.string.dwell_stop_photo_change)
        } else {
            binding.dwellPhotoPreview.visibility = View.GONE
            binding.dwellPhotoButton.setText(R.string.dwell_stop_photo_add)
        }

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.dwell_stop_title)
            .setView(binding.root)
            .setPositiveButton(R.string.day_note_save) { _, _ ->
                store.setDwellNote(dateIso, stop.noteKey, binding.dwellNoteText.text?.toString())
                onChanged()
            }
            .setNegativeButton(R.string.export_cancel, null)
        if (!currentNote.isNullOrEmpty() || currentPhoto != null) {
            builder.setNeutralButton(R.string.dwell_stop_clear) { _, _ ->
                if (currentPhoto != null) {
                    photoStore.deletePhotoFiles(dateIso, currentPhoto)
                    store.setDwellPhoto(dateIso, stop.noteKey, null)
                }
                store.setDwellNote(dateIso, stop.noteKey, null)
                onChanged()
            }
        }
        val dialog = builder.show()
        binding.dwellPhotoButton.setOnClickListener {
            store.setDwellNote(dateIso, stop.noteKey, binding.dwellNoteText.text?.toString())
            dialog.dismiss()
            onAddPhoto()
        }
    }
}
