package com.dayatlas.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Shared activity base: marks outbound (non-DayAtlas) intents so
 * [RecentsHider] keeps the task alive while the user is in system
 * Settings / permission UI and can press Back to return.
 * Also pads the toolbar below the status bar so the title does not
 * collide with the clock / system icons.
 */
open class DayAtlasActivity : AppCompatActivity() {
    override fun setContentView(view: View?) {
        super.setContentView(view)
        applyStatusBarInsetToToolbar()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyStatusBarInsetToToolbar()
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        applyStatusBarInsetToToolbar()
    }

    private fun applyStatusBarInsetToToolbar() {
        val toolbar = findViewById<View?>(R.id.toolbar) ?: return
        val baseLeft = toolbar.paddingLeft
        val baseRight = toolbar.paddingRight
        val baseBottom = toolbar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(baseLeft, top, baseRight, baseBottom)
            insets
        }
        ViewCompat.requestApplyInsets(toolbar)
    }

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
