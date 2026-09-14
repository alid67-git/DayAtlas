package com.dayatlas.app.billing

import android.content.Context
import com.dayatlas.app.prefs.AppPrefs
import java.util.concurrent.TimeUnit

/**
 * A single hardcoded redeem code that grants a local, Play-Billing-
 * independent unlock of the Play build's paywall for 30 days - for
 * reviewers, press, or friends who shouldn't need a real subscription.
 * There is no per-device or per-person limit and no server: whoever has
 * the code can redeem it on any install, so hand it out sparingly rather
 * than publish it anywhere.
 */
object PromoCode {
    private const val CODE = "DAYATLASDOST"
    private val UNLOCK_DURATION_MILLIS = TimeUnit.DAYS.toMillis(30)

    /** Returns true and grants 30 days of access if [input] matches the code. */
    fun redeem(context: Context, input: String): Boolean {
        if (!input.trim().equals(CODE, ignoreCase = true)) return false
        AppPrefs(context).promoUnlockExpiresAtMillis =
            System.currentTimeMillis() + UNLOCK_DURATION_MILLIS
        return true
    }

    fun isActive(context: Context): Boolean =
        System.currentTimeMillis() < AppPrefs(context).promoUnlockExpiresAtMillis
}
