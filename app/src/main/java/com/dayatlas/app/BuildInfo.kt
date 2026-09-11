package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Alt menü geldi: Günlük / İstatistikler / Rotalar / Daha fazla. " +
            "İlk adımda iskelet; istatistik ve rota içerikleri sonraki " +
            "sürümlerde dolacak. Ayarlar ve Yardım ‘Daha fazla’dan açılır."
}
