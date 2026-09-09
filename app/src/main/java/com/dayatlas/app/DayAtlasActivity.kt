package com.dayatlas.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Shared activity base: marks outbound (non-DayAtlas) intents so
 * [RecentsHider] keeps the task alive while the user is in system
 * Settings / permission UI and can press Back to return.
 */
open class DayAtlasActivity : AppCompatActivity() {
    override fun startActivity(intent: Intent, options: Bundle?) {
        maybeRetainForExternal(intent)
        super.startActivity(intent, options)
    }

    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        maybeRetainForExternal(intent)
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    private fun maybeRetainForExternal(intent: Intent) {
        val targetPkg = intent.component?.packageName
        // Explicit component targeting us (Settings/Route) - stay removable.
        if (targetPkg == packageName) return
        // Implicit / external (permission controller, system Settings, OEM
        // screens). If we can't tell, retain - safer than killing mid-flow.
        RecentsHider.retainForExternalNavigation()
    }
}
