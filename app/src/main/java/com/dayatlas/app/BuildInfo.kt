package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Haritada “kayıt yok” yazısı okunaklı kutuda. Farklı güne gidince " +
            "yanında bugüne dön ikonu. Otomatik güncelleme, görev " +
            "değiştiriciden çıkınca iptal olmuyor; kurulum için bildirim " +
            "çıkıyor. Üst çubuk saatle daha net ayrıldı."
}
