package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Haritada başka bir güne geçince o günün mesafesi, son noktası ve " +
            "nokta sayısı artık gün seçicinin hemen altında, üç hücreli " +
            "şeritte görünüyor. Üst blok bugünün kaydına ait kalmaya devam " +
            "eder."
}
