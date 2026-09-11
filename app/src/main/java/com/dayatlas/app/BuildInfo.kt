package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Günlük sekme yenilendi: marka şeridi, 2×2 sürüklenen veri kartları " +
            "ve üç renkli hız/süre şeridi. Kartları uzun basıp sıralayabilir, " +
            "Ayarlar’dan göstereceklerinizi seçebilirsiniz."
}
