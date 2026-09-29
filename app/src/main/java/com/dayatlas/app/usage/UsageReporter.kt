package com.dayatlas.app.usage

import android.os.Handler
import android.os.Looper
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.prefs.AppPrefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Anonymous usage pings (no GPS, no account, no device identifiers sent).
 *
 * - **Installs:** each fresh install hits once (local flag).
 * - **DAU:** each install hits once per local calendar day.
 *
 * Counts live on a public free counter API; Settings reads them back so we
 * can see roughly how many people run the sideload build.
 */
object UsageReporter {
    private const val BASE = "https://countapi.mileshilliard.com/api/v1"
    /** Public counter key — unique enough to avoid collisions with other apps. */
    const val INSTALLS_KEY = "dayatlas_alid67_installs"

    private val io by lazy { Executors.newSingleThreadExecutor() }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    data class Stats(
        val installs: Long?,
        val activeToday: Long?,
    )

    fun dauKey(dateIso: String): String =
        "dayatlas_alid67_dau_" + dateIso.replace("-", "")

    /** Best-effort; never throws to the caller. */
    fun maybePing(prefs: AppPrefs) {
        io.execute {
            runCatching {
                ensureInstallId(prefs)
                if (!prefs.usageInstallCounted) {
                    if (hit(INSTALLS_KEY) != null) {
                        prefs.usageInstallCounted = true
                    }
                }
                val today = DayTitle.iso(DayTitle.localToday())
                if (prefs.lastUsagePingDay != today) {
                    if (hit(dauKey(today)) != null) {
                        prefs.lastUsagePingDay = today
                    }
                }
            }
        }
    }

    fun fetchStats(onResult: (Stats) -> Unit) {
        val today = DayTitle.iso(DayTitle.localToday())
        io.execute {
            val installs = runCatching { get(INSTALLS_KEY) }.getOrNull()
            val active = runCatching { get(dauKey(today)) }.getOrNull()
            main.post { onResult(Stats(installs = installs, activeToday = active)) }
        }
    }

    private fun ensureInstallId(prefs: AppPrefs) {
        if (prefs.installId.isNullOrEmpty()) {
            prefs.installId = UUID.randomUUID().toString()
        }
    }

    internal fun hit(key: String): Long? = request("hit", key)

    internal fun get(key: String): Long? = request("get", key)

    private fun request(action: String, key: String): Long? {
        val connection = URL("$BASE/$action/$key").openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.requestMethod = "GET"
        try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseValue(body)
        } finally {
            connection.disconnect()
        }
    }

    /** Exposed for unit tests. */
    internal fun parseValue(body: String): Long? {
        val raw = JSONObject(body).opt("value") ?: return null
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
    }
}
