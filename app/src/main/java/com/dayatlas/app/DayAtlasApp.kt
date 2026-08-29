package com.dayatlas.app

import android.app.Application
import com.dayatlas.app.location.TrackingController
import com.dayatlas.app.prefs.AppPrefs

class DayAtlasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = AppPrefs(this)
        TrackingController.onAppStart(this, prefs)
    }
}
