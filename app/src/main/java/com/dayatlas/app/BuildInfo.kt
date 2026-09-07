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
            "son sürüm sessizce kontrol ediliyor, yeni bir sürüm varsa hiç " +
            "sormadan indiriliyor. Kurulum anında Android'in kendi kurulum " +
            "ekranı yine de çıkar - bunu hiçbir uygulama atlayamaz. " +
            "Ayarlar'a da elle kontrol için bir düğme eklendi."
}
