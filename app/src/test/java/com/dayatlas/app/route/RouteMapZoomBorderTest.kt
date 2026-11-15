package com.dayatlas.app.route

import com.dayatlas.app.data.JumpFilter
import com.dayatlas.app.data.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class RouteMapZoomBorderTest {
    @Test
    fun preferredBorderWhenMapIsTallEnough() {
        assertEquals(96, RouteMapController.safeZoomBorder(1080, 800))
    }

    @Test
    fun shrinksBorderForShortLandscapePane() {
        // 160px tall: fixed border=96 still fits; (160 - 48) / 2 = 56 caps it.
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

    @Test
    fun fitDropsJumpOutliersSoLocalRouteIsNotCountryScale() {
        val geo = listOf(
            GeoPoint(7.88, 98.39),
            GeoPoint(7.89, 98.40),
            GeoPoint(15.0, 100.0), // teleport outlier
            GeoPoint(7.90, 98.41),
        )
        val jumps = listOf(
            JumpFilter.Jump(
                index = 2,
                point = TrackPoint(0L, 15.0, 100.0, null),
                distanceMeters = 800_000.0,
                speedKmh = 500.0,
                deltaMillis = 60_000L,
            ),
        )
        val fit = RouteMapController.fitPointsExcludingJumps(geo, jumps)
        assertEquals(3, fit.size)
        assertTrue(fit.none { it.latitude > 10.0 })
    }
}
