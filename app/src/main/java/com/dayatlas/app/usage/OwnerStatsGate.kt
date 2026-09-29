package com.dayatlas.app.usage

import android.content.Context
import com.dayatlas.app.prefs.AppPrefs

/**
 * Unlocks the usage-stats row in Settings for the app owner only.
 * Same pattern as [com.dayatlas.app.billing.PromoCode]: a local secret,
 * no server, no Google account. Hand the code only to yourself.
 */
object OwnerStatsGate {
    private const val CODE = "DAYATLASOWNER"

    fun isUnlocked(context: Context): Boolean =
        AppPrefs(context).ownerStatsUnlocked

    fun unlock(context: Context, input: String): Boolean {
        if (!input.trim().equals(CODE, ignoreCase = true)) return false
        AppPrefs(context).ownerStatsUnlocked = true
        return true
    }

    fun lock(context: Context) {
        AppPrefs(context).ownerStatsUnlocked = false
    }
}
