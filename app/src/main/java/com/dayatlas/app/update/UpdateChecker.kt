package com.dayatlas.app.update

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.regex.Pattern

/** A newer APK found on GitHub than the one currently installed. */
data class UpdateInfo(val version: String, val downloadUrl: String)

private const val RELEASE_API_URL =
    "https://api.github.com/repos/alid67-git/DayAtlas/releases/tags/android-latest"
private const val APK_ASSET_NAME = "DayAtlas.apk"

// The CI workflow (android.yml) embeds "v<versionName>" into the release
// name so the app can tell whether that release is newer than itself
// without a separate version-tagging scheme.
private val VERSION_IN_NAME: Pattern = Pattern.compile("v[\\d.]+$")

/**
 * Checks GitHub's rolling "android-latest" release against [currentVersion]
 * (typically BuildConfig.VERSION_NAME). Best-effort: [onResult] fires on the
 * main thread with null when already up to date, or when the check fails for
 * any reason (offline, rate-limited, malformed response) - this must never
 * crash or block the caller.
 */
object UpdateChecker {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun check(currentVersion: String, onResult: (UpdateInfo?) -> Unit) {
        // Debug builds carry a "-debug" versionNameSuffix; strip it so a
        // developer's build compares against the same baseline as release.
        val normalized = currentVersion.substringBefore("-debug")
        io.execute {
            val result = runCatching { fetch(normalized) }.getOrNull()
            main.post { onResult(result) }
        }
    }

    private fun fetch(currentVersion: String): UpdateInfo? {
        val connection = URL(RELEASE_API_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val name = json.optString("name", "")
            val matcher = VERSION_IN_NAME.matcher(name.trim())
            val releaseVersion = if (matcher.find()) matcher.group() else null
            if (releaseVersion == null || releaseVersion == "v$currentVersion") return null

            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name") != APK_ASSET_NAME) continue
                val url = asset.optString("browser_download_url")
                if (url.isNotEmpty()) return UpdateInfo(releaseVersion, url)
            }
            return null
        } finally {
            connection.disconnect()
        }
    }
}
