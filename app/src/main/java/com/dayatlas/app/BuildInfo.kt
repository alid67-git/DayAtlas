package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "GPX aralık dışa aktarmada her gün ayrı iz olarak yazılıyor; " +
            "noktaların gerçek UTC zaman damgası korunuyor. Dosya adına " +
            ".gpx otomatik ekleniyor. Üst çubuk saatle çakışmasın diye " +
            "aşağı alındı. Örnekleme aralıkları: 30 sn, 1 dk (önerilen), " +
            "3 dk, 5 dk — daha sık nokta, daha düzgün çizgi."
}
