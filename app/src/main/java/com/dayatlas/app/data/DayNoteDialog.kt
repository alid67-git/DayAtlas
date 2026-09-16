package com.dayatlas.app.data

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dayatlas.app.R
import com.dayatlas.app.databinding.DialogDayNoteBinding

/** Adds, edits, or clears a single day's free-form note (see [DayStore.setNote]). */
object DayNoteDialog {
    fun show(
        activity: AppCompatActivity,
        store: DayStore,
        dateIso: String,
        onChanged: () -> Unit,
    ) {
        val binding = DialogDayNoteBinding.inflate(LayoutInflater.from(activity))
        val currentNote = store.load(dateIso)?.note
        binding.dayNoteText.setText(currentNote.orEmpty())

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.day_note_title)
            .setView(binding.root)
            .setPositiveButton(R.string.day_note_save) { _, _ ->
                store.setNote(dateIso, binding.dayNoteText.text?.toString())
                onChanged()
            }
            .setNegativeButton(R.string.export_cancel, null)
        if (!currentNote.isNullOrEmpty()) {
            builder.setNeutralButton(R.string.day_note_clear) { _, _ ->
                store.setNote(dateIso, null)
                onChanged()
            }
        }
        builder.show()
    }
}
