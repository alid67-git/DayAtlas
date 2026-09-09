package com.dayatlas.app

/**
 * Bumped by hand alongside versionName/versionCode in app/build.gradle.kts.
 * Shown once, in a dialog, the first time this version runs (see
 * MainActivity.maybeShowChangelog) - the native-Kotlin equivalent of
 * RideAtlas/MediaAtlas's kAppBuildNote.
 */
object BuildInfo {
    const val BUILD_NOTE =
        "Görev değiştiricide (son uygulamalar) kart bırakma sorunu için " +
            "daha kesin yöntem: uygulama arka plana geçince görev artık " +
            "yalnızca gizlenmiyor, tamamen kaldırılıyor. Ana ekran " +
            "simgesinden yeniden açılır; AlarmManager ile günlük kayıt " +
            "arka planda aynı şekilde devam eder. Sistem ayarları / izin " +
            "ekranlarından Geri ile dönüş korunuyor."
}
