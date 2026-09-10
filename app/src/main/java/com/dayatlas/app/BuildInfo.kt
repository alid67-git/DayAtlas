package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Google Drive yedek: Ayarlar’dan bir Drive klasörü seçin, günlük " +
            "otomatik yedeği açın. Günde bir kez JSON+GPX o klasöre " +
            "kopyalanır; Drive uygulaması buluta senkronlar."
}
