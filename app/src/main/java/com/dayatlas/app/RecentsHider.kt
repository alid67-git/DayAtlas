package com.dayatlas.app

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Keeps DayAtlas out of the overview / recent-apps switcher.
 *
 * Manifest `excludeFromRecents` and [ActivityManager.AppTask.setExcludeFromRecents]
 * are enough on stock AOSP, but some OEM skins (notably Samsung One UI) still
 * paint a card while the task is alive in the background. The reliable fix
 * those skins honor is to actually finish and remove the task once the UI is
 * no longer visible - which is what apps that "disappear from running apps"
 * typically do.
 *
 * Sampling continues regardless: [com.dayatlas.app.location.SampleScheduler]
 * / AlarmManager / [com.dayatlas.app.location.SampleService] do not depend on
 * an activity or a recents task. Removing the task is not the same as the
 * user swiping the card away in OEM "kill on swipe" implementations.
 *
 * External navigations (system Settings, permission controllers, OEM
 * autostart screens) set [retainForExternalNavigation] so Back still returns
 * here; Home / app-switch away still removes the card after a short delay
 * (long enough that a permission-dialog flicker doesn't tear the task down).
 */
object RecentsHider {
    private const val REMOVE_DELAY_MS = 700L

    private val handler = Handler(Looper.getMainLooper())
    private var startedCount = 0
    private var pendingRemove: Runnable? = null

    @Volatile
    private var retainForExternal = false

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    exclude(app)
                }

                override fun onActivityStarted(activity: Activity) {
                    startedCount++
                    cancelPending()
                }

                override fun onActivityResumed(activity: Activity) {
                    // Back from an external screen: drop the retain flag so the
                    // next real leave (Home / switcher) can remove the task.
                    retainForExternal = false
                    exclude(app)
                }

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) {
                    startedCount = (startedCount - 1).coerceAtLeast(0)
                    if (startedCount == 0) {
                        scheduleRemove(app)
                    }
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    /**
     * Call (or rely on [DayAtlasActivity] doing it) before launching an
     * intent that leaves our process - system Settings, permission UI, etc.
     * Prevents tearing down the task while the user is expected to come Back.
     */
    fun retainForExternalNavigation() {
        retainForExternal = true
    }

    private fun scheduleRemove(app: Application) {
        cancelPending()
        exclude(app)
        if (retainForExternal) return
        val runnable = Runnable { removeTasks(app) }
        pendingRemove = runnable
        handler.postDelayed(runnable, REMOVE_DELAY_MS)
    }

    private fun cancelPending() {
        pendingRemove?.let { handler.removeCallbacks(it) }
        pendingRemove = null
    }

    private fun exclude(app: Application) {
        runCatching {
            val am = app.getSystemService(ActivityManager::class.java) ?: return
            am.appTasks.forEach { it.setExcludeFromRecents(true) }
        }
    }

    private fun removeTasks(app: Application) {
        runCatching {
            val am = app.getSystemService(ActivityManager::class.java) ?: return
            // Snapshot: finishAndRemoveTask mutates the list.
            am.appTasks.toList().forEach { task ->
                task.setExcludeFromRecents(true)
                task.finishAndRemoveTask()
            }
        }
    }
}
