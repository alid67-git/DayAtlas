package com.dayatlas.app.boot

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Best-effort deep links into OEM-specific "autostart" / "protected apps" screens.
 * Android has no public API for this; these component names are undocumented
 * and can change between firmware versions, so every launch is wrapped and
 * failures just move on to the next candidate (or report nothing found).
 */
object OemAutostart {
    private data class Candidate(val pkg: String, val cls: String)

    private val candidates: Map<String, List<Candidate>> = mapOf(
        "xiaomi" to listOf(
            Candidate("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
        "huawei" to listOf(
            Candidate("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Candidate("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ),
        "honor" to listOf(
            Candidate("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ),
        "oppo" to listOf(
            Candidate("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Candidate("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ),
        "realme" to listOf(
            Candidate("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ),
        "vivo" to listOf(
            Candidate("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Candidate("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ),
        "samsung" to listOf(
            Candidate("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ),
        "oneplus" to listOf(
            Candidate("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ),
        "meizu" to listOf(
            Candidate("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC"),
        ),
        "asus" to listOf(
            Candidate("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        ),
    )

    private fun manufacturerKey(): String? {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: return null
        return candidates.keys.firstOrNull { manufacturer.contains(it) }
    }

    fun isKnownOem(): Boolean = manufacturerKey() != null

    /** Tries each known candidate screen in order; returns true if one launched. */
    fun open(context: Context): Boolean {
        val key = manufacturerKey() ?: return false
        val list = candidates[key] ?: return false
        for (candidate in list) {
            val intent = Intent().apply {
                component = ComponentName(candidate.pkg, candidate.cls)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // try next candidate
            } catch (_: SecurityException) {
                // try next candidate
            }
        }
        return false
    }
}
