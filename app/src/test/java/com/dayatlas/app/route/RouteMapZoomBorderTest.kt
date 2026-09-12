package com.dayatlas.app.route

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteMapZoomBorderTest {
    @Test
    fun preferredBorderWhenMapIsTallEnough() {
        assertEquals(160, RouteMapController.safeZoomBorder(1080, 800))
    }

    @Test
    fun shrinksBorderForShortLandscapePane() {
        // 160px tall: fixed border=160 would make height - 320 negative → blank map.
        // Safe border keeps min 48px inner → (160 - 48) / 2 = 56.
        assertEquals(56, RouteMapController.safeZoomBorder(640, 160))
    }

    @Test
    fun zeroWhenMapNotLaidOut() {
        assertEquals(0, RouteMapController.safeZoomBorder(0, 200))
        assertEquals(0, RouteMapController.safeZoomBorder(200, 0))
    }

    @Test
    fun veryShortPaneUsesZeroBorderStillPositiveInner() {
        assertEquals(0, RouteMapController.safeZoomBorder(400, 40))
    }
}
