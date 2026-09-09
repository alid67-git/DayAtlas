package com.dayatlas.app.export

import android.app.DatePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dayatlas.app.R
import com.dayatlas.app.RecentsHider
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.databinding.DialogExportGpxBinding
import java.time.LocalDate

/**
 * Asks for single-day vs range, dates, and a file name, then shares a GPX
 * via the system chooser (FileProvider + ACTION_SEND).
 */
object GpxExportDialog {
    fun show(activity: AppCompatActivity, store: DayStore, initialDate: LocalDate = DayTitle.localToday()) {
        val binding = DialogExportGpxBinding.inflate(LayoutInflater.from(activity))
        var from = initialDate
        var to = initialDate

        fun refreshLabels() {
            binding.startDate.text = DayTitle.iso(from)
            binding.endDate.text = DayTitle.iso(to)
            val range = binding.modeRange.isChecked
            binding.endDateLabel.visibility = if (range) View.VISIBLE else View.GONE
            binding.endDate.visibility = if (range) View.VISIBLE else View.GONE
            if (!binding.fileName.hasFocus()) {
                val end = if (range) to else from
                binding.fileName.setText(GpxExporter.defaultFileName(from, end))
            }
        }

        fun pickDate(current: LocalDate, onPicked: (LocalDate) -> Unit) {
            DatePickerDialog(
                activity,
                { _, year, month, dayOfMonth ->
                    onPicked(LocalDate.of(year, month + 1, dayOfMonth))
                    refreshLabels()
                },
                current.year,
                current.monthValue - 1,
                current.dayOfMonth,
            ).show()
        }

        binding.exportMode.setOnCheckedChangeListener { _, _ -> refreshLabels() }
        binding.startDate.setOnClickListener {
            pickDate(from) { picked ->
                from = picked
                if (to.isBefore(from)) to = from
            }
        }
        binding.endDate.setOnClickListener {
            pickDate(to) { picked ->
                to = picked
                if (from.isAfter(to)) from = to
            }
        }
        refreshLabels()

        AlertDialog.Builder(activity)
            .setTitle(R.string.export_title)
            .setView(binding.root)
            .setNegativeButton(R.string.export_cancel, null)
            .setPositiveButton(R.string.export_share, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val range = binding.modeRange.isChecked
                        val end = if (range) to else from
                        if (end.isBefore(from)) {
                            Toast.makeText(activity, R.string.export_bad_range, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val stem = GpxExporter.sanitizeFileName(
                            binding.fileName.text?.toString().orEmpty(),
                        )
                        val intent = GpxExporter.buildShareIntent(
                            activity,
                            store,
                            from,
                            end,
                            stem,
                        )
                        if (intent == null) {
                            Toast.makeText(activity, R.string.export_no_points, Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        RecentsHider.retainForExternalNavigation()
                        activity.startActivity(intent)
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }
}
