package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "İstatistikler sekmesi: Bugün / 7 gün / 30 gün / Bu ay / Tümü " +
            "aralığında toplam mesafe, aktif gün, hız, süre ve günlük " +
            "mesafe çubukları."
}
