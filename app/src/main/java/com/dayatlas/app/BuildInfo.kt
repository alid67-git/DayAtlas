package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Uygulama artık kendini güncelleyebiliyor: açılışta GitHub'daki en " +
            "son sürüm kontrol ediliyor, yeni bir sürüm varsa indirip kurman " +
            "için soruyor. Ayarlar'a da elle kontrol için bir düğme eklendi."
}
