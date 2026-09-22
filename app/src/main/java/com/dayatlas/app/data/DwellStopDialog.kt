package com.dayatlas.app.data

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dayatlas.app.R
import com.dayatlas.app.databinding.DialogDwellStopBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shows dwell duration and an optional per-stop note (see [DayStore.setDwellNote]). */
object DwellStopDialog {
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    fun show(
        activity: AppCompatActivity,
        store: DayStore,
        dateIso: String,
        stop: DwellStops.Stop,
        onChanged: () -> Unit,
    ) {
        val binding = DialogDwellStopBinding.inflate(LayoutInflater.from(activity))
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(stop.startMillis).atZone(zone).toLocalTime().format(TIME_FMT)
        val end = Instant.ofEpochMilli(stop.endMillis).atZone(zone).toLocalTime().format(TIME_FMT)
        val duration = DayTitle.formatDuration(stop.durationMillis)
        binding.dwellSummary.text =
            activity.getString(R.string.dwell_stop_summary, start, end, duration)

        val currentNote = store.load(dateIso)?.dwellNotes?.get(stop.noteKey)
        binding.dwellNoteText.setText(currentNote.orEmpty())

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.dwell_stop_title)
            .setView(binding.root)
            .setPositiveButton(R.string.day_note_save) { _, _ ->
                store.setDwellNote(dateIso, stop.noteKey, binding.dwellNoteText.text?.toString())
                onChanged()
            }
            .setNegativeButton(R.string.export_cancel, null)
        if (!currentNote.isNullOrEmpty()) {
            builder.setNeutralButton(R.string.day_note_clear) { _, _ ->
                store.setDwellNote(dateIso, stop.noteKey, null)
                onChanged()
            }
        }
        builder.show()
    }
}
