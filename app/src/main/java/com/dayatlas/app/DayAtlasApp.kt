package com.dayatlas.app

import android.app.Application
import com.dayatlas.app.backup.DriveFolderBackup
import com.dayatlas.app.location.TrackingController
import com.dayatlas.app.prefs.AppPrefs
import java.io.File
import org.osmdroid.config.Configuration

class DayAtlasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RecentsHider.install(this)
        val prefs = AppPrefs(this)
        AppLocale.applyFromPrefs(prefs)
        configureOsmdroid()
        TrackingController.onAppStart(this, prefs)
        DriveFolderBackup.maybeRunDaily(this)
    }

    /**
     * OSM's tile usage policy requires a distinguishing User-Agent; the app
     * cache dir keeps tile storage private (no legacy storage permission).
     * Done once here since the route screen may be opened many times.
     */
    private fun configureOsmdroid() {
        val config = Configuration.getInstance()
        config.userAgentValue = packageName
        val base = getDir("osmdroid", MODE_PRIVATE)
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")
    }
}
