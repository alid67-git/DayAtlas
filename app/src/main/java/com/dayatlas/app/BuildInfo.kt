package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Aynı yerde kalınca GPS örneklemesi kademeli seyreltiliyor " +
            "(~25 m içinde art arda 3 ölçüm → bir üst aralık, en fazla 5 dk). " +
            "Hareket edince ayarlardaki aralığa döner; gece sabit otururken " +
            "pil tüketimini azaltır."
}
