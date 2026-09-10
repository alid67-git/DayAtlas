package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Güncelleme indirme sonrası kurulum güçlendirildi (bildirim + " +
            "otomatik kurulum denemesi). Bu sürümü bir kez elle kurmanız " +
            "gerekebilir; sonraki güncellemeler yine otomatik kurulur."
}
