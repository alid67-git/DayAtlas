package com.dayatlas.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dayatlas.app.location.TrackingController
import com.dayatlas.app.prefs.AppPrefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        TrackingController.onAppStart(context, AppPrefs(context))
    }
}

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = AppPrefs(context)
        prefs.clearPendingUpdate()
        TrackingController.onAppStart(context, prefs)
    }
}
