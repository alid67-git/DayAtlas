package com.dayatlas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.dayatlas.app.data.DayNoteDialog
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.data.Geo
import com.dayatlas.app.data.JumpCleanupDialog
import com.dayatlas.app.data.JumpFilter
import com.dayatlas.app.data.PhotoStore
import com.dayatlas.app.data.PhotoViewerDialog
import com.dayatlas.app.data.SpeedStats
import com.dayatlas.app.databinding.ActivityDayDetailBinding
import com.dayatlas.app.export.GpxExportDialog
import com.dayatlas.app.prefs.AppPrefs
import com.dayatlas.app.route.DayStatKind
import com.dayatlas.app.route.DayStatsAdapter
import com.dayatlas.app.route.RouteMapController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

/**
 * Full-detail view for a single past day (Routes list or Statistics bar).
 * [EXTRA_RETURN_SOURCE] remembers which MainActivity tab to restore on back.
 */
class DayDetailActivity : DayAtlasActivity() {
    private lateinit var binding: ActivityDayDetailBinding
    private lateinit var dateIso: String
    private lateinit var date: LocalDate
    private lateinit var statsAdapter: DayStatsAdapter
    private var returnSource: String? = null
    private var dayPhotos: List<String> = emptyList()
    private val store by lazy { DayStore(this) }
    private val photoStore by lazy { PhotoStore(this) }
    private val prefs by lazy { AppPrefs(this) }

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        photoStore.addPhotoAsync(dateIso, uri, store) { ok ->
            if (!ok) {
                Toast.makeText(this, R.string.day_photo_add_failed, Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dateIso = intent.getStringExtra(EXTRA_DATE_ISO) ?: run {
            finish()
            return
        }
        date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: run {
            finish()
            return
        }
        returnSource = intent.getStringExtra(EXTRA_RETURN_SOURCE)
        binding = ActivityDayDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.routeMap.setTileSource(TileSourceFactory.MAPNIK)
        binding.routeMap.setMultiTouchControls(true)
        binding.routeMap.isTilesScaledToDpi = true
        binding.routeMap.setBackgroundColor(0xFFE8EEF4.toInt())

        binding.toolbar.title = DayTitle.format(date)
        binding.toolbar.setNavigationOnClickListener { navigateBack() }
        binding.exportButton.setOnClickListener {
            GpxExportDialog.show(this, store, date)
        }
        binding.previousDay.setOnClickListener { shiftDay(-1) }
        binding.nextDay.setOnClickListener { shiftDay(1) }
        binding.dayNoteButton.setOnClickListener {
            DayNoteDialog.show(this, store, dateIso) { refresh() }
        }
        binding.mapJumpsButton.setOnClickListener {
            JumpCleanupDialog.show(this, store, dateIso) { refresh() }
        }
        binding.mapPhotosButton.setOnClickListener { onPhotosTap() }
        binding.mapPhotosButton.setOnLongClickListener {
            if (dayPhotos.size < PhotoStore.MAX_PHOTOS_PER_DAY) {
                pickPhotoLauncher.launch("image/*")
                true
            } else {
                false
            }
        }
        statsAdapter = DayStatsAdapter(onReordered = {})
        binding.dayStats.adapter = statsAdapter
        statsAdapter.attachTo(binding.dayStats, spanCount = DayStatsAdapter.GRID_SPAN)

        refresh()
    }

    private fun onPhotosTap() {
        when {
            dayPhotos.isEmpty() -> pickPhotoLauncher.launch("image/*")
            else -> PhotoViewerDialog.show(
                this,
                store,
                photoStore,
                dateIso,
                dayPhotos.first(),
            ) { refresh() }
        }
    }

    /** Left/right map chevrons — calendar day, same as the Daily tab. */
    private fun shiftDay(delta: Int) {
        val today = DayTitle.localToday()
        val target = date.plusDays(delta.toLong())
        if (target.isAfter(today)) return
        date = target
        dateIso = DayTitle.iso(date)
        intent.putExtra(EXTRA_DATE_ISO, dateIso)
        binding.toolbar.title = DayTitle.format(date)
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        navigateBack()
    }

    private fun navigateBack() {
        val tab = when (returnSource) {
            RETURN_STATS -> R.id.nav_stats
            RETURN_ROUTES -> R.id.nav_routes
            else -> null
        }
        if (tab != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_TAB, tab),
            )
        }
        finish()
    }

    override fun onResume() {
        super.onResume()
        binding.routeMap.onResume()
    }

    override fun onPause() {
        binding.routeMap.onPause()
        super.onPause()
    }

    private fun refresh() {
        val record = store.load(dateIso)
        val points = record?.points.orEmpty()
        val jumps = JumpFilter.findJumps(points)

        val today = DayTitle.localToday()
        binding.nextDay.visibility = if (date.isBefore(today)) View.VISIBLE else View.GONE
        binding.previousDay.visibility = View.VISIBLE

        val emDash = getString(R.string.em_dash)
        val speed = SpeedStats.compute(points)
        statsAdapter.submit(
            listOf(
                DayStatKind.DISTANCE to if (points.isEmpty()) {
                    emDash
                } else {
                    DayTitle.formatDistance(record?.distanceMeters ?: Geo.pathLengthMeters(points))
                },
                DayStatKind.LAST_POINT to (
                    points.lastOrNull()?.let { point ->
                        Instant.ofEpochMilli(point.timeMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalTime()
                            .format(TIME_FMT)
                    } ?: emDash
                    ),
                DayStatKind.POINT_COUNT to (record?.checkCount ?: points.size).toString(),
                DayStatKind.GPS_INTERVAL to formatGpsInterval(prefs.effectiveIntervalSeconds),
                DayStatKind.MAX_SPEED to if (points.size < 2) emDash else DayTitle.formatSpeed(speed.maxSpeedKmh),
                DayStatKind.AVG_SPEED to if (points.size < 2) emDash else DayTitle.formatSpeed(speed.avgSpeedKmh),
                DayStatKind.ACTIVE_DURATION to if (points.size < 2) {
                    emDash
                } else {
                    DayTitle.formatDuration(speed.activeMillis)
                },
            ),
        )

        binding.mapJumpsButton.visibility = if (jumps.isEmpty()) View.GONE else View.VISIBLE

        binding.dayNoteButton.imageTintList = ContextCompat.getColorStateList(
            this,
            if (record?.note.isNullOrEmpty()) R.color.md_theme_on_surface else R.color.status_on,
        )

        applyDayPhotos(record?.photos.orEmpty())

        RouteMapController.show(
            map = binding.routeMap,
            context = this,
            points = points,
            emptyState = binding.emptyState,
            dateIso = dateIso,
            jumps = jumps,
            fitCamera = true,
            onJumpTap = { jump ->
                JumpCleanupDialog.confirmDelete(this, store, dateIso, jump) { refresh() }
            },
        )
    }

    private fun formatGpsInterval(seconds: Int): String = when (seconds) {
        10 -> getString(R.string.interval_10s_short)
        20 -> getString(R.string.interval_20s_short)
        30 -> getString(R.string.interval_30s_short)
        60 -> getString(R.string.interval_1_short)
        180 -> getString(R.string.interval_3_short)
        300 -> getString(R.string.interval_5_short)
        else -> getString(R.string.interval_seconds_short, seconds)
    }

    private fun applyDayPhotos(photos: List<String>) {
        dayPhotos = photos
        val hasPhotos = photos.isNotEmpty()
        binding.mapPhotosButton.alpha = if (hasPhotos) 1f else 0.45f
        binding.mapPhotosButton.imageTintList = ContextCompat.getColorStateList(
            this,
            if (hasPhotos) R.color.status_on else R.color.md_theme_on_surface,
        )
        binding.mapPhotosButton.contentDescription = getString(
            if (hasPhotos) R.string.day_photo_thumbnail else R.string.day_photo_add,
        )
    }

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
        private const val EXTRA_DATE_ISO = "date_iso"
        const val EXTRA_RETURN_SOURCE = "return_source"
        const val RETURN_STATS = "stats"
        const val RETURN_ROUTES = "routes"

        fun intent(
            context: Context,
            dateIso: String,
            returnSource: String? = null,
        ): Intent =
            Intent(context, DayDetailActivity::class.java)
                .putExtra(EXTRA_DATE_ISO, dateIso)
                .putExtra(EXTRA_RETURN_SOURCE, returnSource)
    }
}
