package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Üst sağdaki GPX dışa aktar ve Ayarlar simgeleri kaldırıldı — " +
            "GPX için Rotalar, ayarlar için Daha fazla sekmesi kullanılır."
}
