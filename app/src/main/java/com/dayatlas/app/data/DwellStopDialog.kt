package com.dayatlas.app.data

import androidx.appcompat.app.AppCompatActivity

/** @deprecated Prefer [PointNoteDialog.showForDwell]; kept for call-site clarity. */
object DwellStopDialog {
    fun show(
        activity: AppCompatActivity,
        store: DayStore,
        photoStore: PhotoStore,
        dateIso: String,
        stop: DwellStops.Stop,
        onAddPhoto: () -> Unit,
        onChanged: () -> Unit,
    ) {
        PointNoteDialog.showForDwell(
            activity = activity,
            store = store,
            photoStore = photoStore,
            dateIso = dateIso,
            stop = stop,
            onAddPhoto = onAddPhoto,
            onChanged = onChanged,
        )
    }
}
