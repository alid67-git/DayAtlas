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
 * Note + optional photo for any track point or ≥15 min dwell.
 * Storage reuses [DayRecord.dwellNotes] / [DayRecord.dwellPhotos] keyed by
 * the point's arrival millis ([TrackPoint.timeMillis]).
 */
object PointNoteDialog {
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    fun showForDwell(
        activity: AppCompatActivity,
        store: DayStore,
        photoStore: PhotoStore,
        dateIso: String,
        stop: DwellStops.Stop,
        onAddPhoto: () -> Unit,
        onChanged: () -> Unit,
    ) {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(stop.startMillis).atZone(zone).toLocalTime().format(TIME_FMT)
        val end = Instant.ofEpochMilli(stop.endMillis).atZone(zone).toLocalTime().format(TIME_FMT)
        val duration = DayTitle.formatDuration(stop.durationMillis)
        show(
            activity = activity,
            store = store,
            photoStore = photoStore,
            dateIso = dateIso,
            noteKey = stop.noteKey,
            titleRes = R.string.dwell_stop_title,
            summary = activity.getString(R.string.dwell_stop_summary, start, end, duration),
            hintRes = R.string.dwell_stop_hint,
            onAddPhoto = onAddPhoto,
            onChanged = onChanged,
        )
    }

    fun showForPoint(
        activity: AppCompatActivity,
        store: DayStore,
        photoStore: PhotoStore,
        dateIso: String,
        point: TrackPoint,
        onAddPhoto: () -> Unit,
        onChanged: () -> Unit,
    ) {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(point.timeMillis).atZone(zone).toLocalTime().format(TIME_FMT)
        val endMs = point.lastTimeMillis.coerceAtLeast(point.timeMillis)
        val summary = if (endMs - point.timeMillis >= 60_000L) {
            val end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalTime().format(TIME_FMT)
            val duration = DayTitle.formatDuration(endMs - point.timeMillis)
            activity.getString(R.string.dwell_stop_summary, start, end, duration)
        } else {
            activity.getString(R.string.point_note_summary, start)
        }
        show(
            activity = activity,
            store = store,
            photoStore = photoStore,
            dateIso = dateIso,
            noteKey = point.timeMillis,
            titleRes = R.string.point_note_title,
            summary = summary,
            hintRes = R.string.point_note_hint,
            onAddPhoto = onAddPhoto,
            onChanged = onChanged,
        )
    }

    private fun show(
        activity: AppCompatActivity,
        store: DayStore,
        photoStore: PhotoStore,
        dateIso: String,
        noteKey: Long,
        titleRes: Int,
        summary: String,
        hintRes: Int,
        onAddPhoto: () -> Unit,
        onChanged: () -> Unit,
    ) {
        val binding = DialogDwellStopBinding.inflate(LayoutInflater.from(activity))
        binding.dwellSummary.text = summary
        binding.dwellHint.setText(hintRes)

        val record = store.load(dateIso)
        val currentNote = record?.dwellNotes?.get(noteKey)
        val currentPhoto = record?.dwellPhotos?.get(noteKey)
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
            .setTitle(titleRes)
            .setView(binding.root)
            .setPositiveButton(R.string.day_note_save) { _, _ ->
                store.setDwellNote(dateIso, noteKey, binding.dwellNoteText.text?.toString())
                onChanged()
            }
            .setNegativeButton(R.string.export_cancel, null)
        if (!currentNote.isNullOrEmpty() || currentPhoto != null) {
            builder.setNeutralButton(R.string.dwell_stop_clear) { _, _ ->
                if (currentPhoto != null) {
                    photoStore.deletePhotoFiles(dateIso, currentPhoto)
                    store.setDwellPhoto(dateIso, noteKey, null)
                }
                store.setDwellNote(dateIso, noteKey, null)
                onChanged()
            }
        }
        val dialog = builder.show()
        binding.dwellPhotoButton.setOnClickListener {
            store.setDwellNote(dateIso, noteKey, binding.dwellNoteText.text?.toString())
            dialog.dismiss()
            onAddPhoto()
        }
    }
}
