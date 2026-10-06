package com.dayatlas.app.backup

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dayatlas.app.R
import com.dayatlas.app.data.DayRecord

/**
 * Walks [candidates] one-by-one with overwrite / keep / cancel dialogs, then
 * invokes [onFinished] with the days the user chose to restore.
 */
object DayRestoreReview {
    private val main = Handler(Looper.getMainLooper())

    fun start(
        activity: AppCompatActivity,
        candidates: List<DayRestore.Candidate>,
        onFinished: (approved: List<DayRecord>) -> Unit,
    ) {
        if (candidates.isEmpty()) {
            onFinished(emptyList())
            return
        }
        reviewNext(activity, candidates, 0, ArrayList(), onFinished)
    }

    private fun reviewNext(
        activity: AppCompatActivity,
        candidates: List<DayRestore.Candidate>,
        index: Int,
        approved: MutableList<DayRecord>,
        onFinished: (List<DayRecord>) -> Unit,
    ) {
        if (activity.isFinishing) return
        if (index >= candidates.size) {
            onFinished(approved)
            return
        }
        val candidate = candidates[index]
        val titleRes = if (candidate.hasLocalTrack) {
            R.string.restore_day_conflict_title
        } else {
            R.string.restore_day_new_title
        }
        val positiveRes = if (candidate.hasLocalTrack) {
            R.string.restore_day_overwrite
        } else {
            R.string.restore_day_import
        }
        AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setMessage(DayRestore.formatCompareMessage(activity, candidate))
            .setPositiveButton(positiveRes) { _, _ ->
                approved.add(candidate.incoming)
                main.post {
                    reviewNext(activity, candidates, index + 1, approved, onFinished)
                }
            }
            .setNeutralButton(R.string.restore_day_skip) { _, _ ->
                main.post {
                    reviewNext(activity, candidates, index + 1, approved, onFinished)
                }
            }
            .setNegativeButton(R.string.restore_day_cancel_all) { _, _ ->
                onFinished(approved)
            }
            .setCancelable(false)
            .show()
    }
}
