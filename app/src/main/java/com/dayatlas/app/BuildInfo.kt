package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Rotalar sekmesi: kayıtlı günler listelenir; dokununca haritada " +
            "açılır, ok ile o günün GPX’i paylaşılır. Üstteki ‘Aralık GPX’ " +
            "çok gün dışa aktarır."
}
