package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Otomatik güncelleme indirdikten sonra kurulum ekranı / “kurmak " +
            "için dokun” bildirimi artık geliyor (önceden indirme bitince " +
            "sessizce kalıyordu). Uygulamayı tekrar açınca da bekleyen " +
            "kurulum hatırlatılır."
}
