package com.dayatlas.app.route

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/**
 * Shared map tile + chrome setup.
 *
 * Standard MAPNIK tiles paint labels in the local script (Thai in Thailand,
 * etc.). The German OSM-Carto fork prefers a Latin / English name when one
 * exists and otherwise transliterates non-Latin names — readable worldwide
 * without an API key.
 */
object MapTiles {
    /**
     * English / Latin–preferring OSM raster tiles (openstreetmap.de style).
     * No API key; attribution required by the tile usage policy.
     */
    val ENGLISH: OnlineTileSourceBase = XYTileSource(
        "OSMEnglish",
        0,
        19,
        256,
        ".png",
        arrayOf("https://tile.openstreetmap.de/"),
        "© OpenStreetMap contributors",
        TileSourcePolicy(
            2,
            TileSourcePolicy.FLAG_NO_BULK
                or TileSourcePolicy.FLAG_NO_PREVENTIVE
                or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
                or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
        ),
    )

    /** Pinch-zoom only — hide the on-map +/- buttons. */
    fun configure(map: MapView) {
        map.setTileSource(ENGLISH)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        // Avoid a pure-white hole while tiles / first camera fit settle.
        map.setBackgroundColor(0xFFE8EEF4.toInt())
    }
}
