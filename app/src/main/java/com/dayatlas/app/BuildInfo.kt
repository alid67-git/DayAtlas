package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Harita artık ayrı ekranda değil — ana ekranın altında. Oklarla " +
            "önceki günlerin rotasına bakabilirsiniz. Araç çubuğundan GPX " +
            "dışa aktarma: tek gün veya tarih aralığı seçin, dosya adını " +
            "istediğiniz gibi yazın, sistem paylaşım ekranından kaydedin " +
            "veya gönderin."
}
