package com.dayatlas.app.data

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dayatlas.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lists detected GPS jumps for a day; tap a row to delete that point.
 */
object JumpCleanupDialog {
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun show(
        activity: AppCompatActivity,
        store: DayStore,
        dateIso: String,
        onChanged: () -> Unit,
    ) {
        val record = store.load(dateIso)
        val jumps = JumpFilter.findJumps(record?.points.orEmpty())
        if (jumps.isEmpty()) {
            Toast.makeText(activity, R.string.jumps_none, Toast.LENGTH_SHORT).show()
            return
        }

        val labels = jumps.map { jump ->
            val time = Instant.ofEpochMilli(jump.point.timeMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(TIME_FMT)
            val dist = DayTitle.formatDistance(jump.distanceMeters)
            val speed = DayTitle.formatSpeed(jump.speedKmh)
            activity.getString(R.string.jump_row, time, dist, speed)
        }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.jumps_title, jumps.size))
            .setItems(labels) { _, which ->
                val jump = jumps[which]
                confirmDelete(activity, store, dateIso, jump, onChanged)
            }
            .setNegativeButton(R.string.export_cancel, null)
            .show()
    }

    fun confirmDelete(
        activity: AppCompatActivity,
        store: DayStore,
        dateIso: String,
        jump: JumpFilter.Jump,
        onChanged: () -> Unit,
    ) {
        val time = Instant.ofEpochMilli(jump.point.timeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(TIME_FMT)
        val dist = DayTitle.formatDistance(jump.distanceMeters)
        AlertDialog.Builder(activity)
            .setTitle(R.string.jump_delete_title)
            .setMessage(activity.getString(R.string.jump_delete_message, time, dist))
            .setPositiveButton(R.string.jump_delete) { _, _ ->
                val updated = store.removePointAt(dateIso, jump.index)
                if (updated == null) {
                    Toast.makeText(activity, R.string.jump_delete_failed, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, R.string.jump_deleted, Toast.LENGTH_SHORT).show()
                    onChanged()
                }
            }
            .setNegativeButton(R.string.export_cancel, null)
            .show()
    }
}
