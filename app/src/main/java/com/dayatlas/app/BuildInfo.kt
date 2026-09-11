package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Günlük: üst blok hep bugün; harita gün gezicisi geçmişi açar " +
            "(mesafe / son nokta / nokta). Tüm kartlar daraltıldı ve " +
            "sürüklenebilir. GPS alma hızı anlık aralığı gösterir. " +
            "Modern mavi-gri-kırmızı palet. Yedek günde bir; GPX uzantısı sabit."
}
