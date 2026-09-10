package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "GPS atlamaları: yeni örnekler ~160 km/sa / 30 km eşiğinin üstünde " +
            "otomatik reddedilir. Haritada turuncu uyarı işaretleri ve " +
            "“Atlamalar” listesiyle eski sıçramaları dokunarak silebilirsiniz."
}
