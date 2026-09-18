package com.dayatlas.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.dayatlas.app.route.DayStatKind
import com.dayatlas.app.route.DayStatsAdapter
import com.dayatlas.app.route.RouteMapController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

/**
 * Full-detail view for a single past day, opened by tapping a row in the
 * Routes list — a bigger map plus the same note/photos/jumps a rider gets
 * on the Daily tab, without routing through Daily's own live-tracking chrome
 * (today's start/stop button, daily-mode banner) which isn't relevant to a
 * day that's already over.
 */
class DayDetailActivity : DayAtlasActivity() {
    private lateinit var binding: ActivityDayDetailBinding
    private lateinit var dateIso: String
    private lateinit var date: LocalDate
    private lateinit var statsAdapter: DayStatsAdapter
    private val store by lazy { DayStore(this) }
    private val photoStore by lazy { PhotoStore(this) }

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
        binding = ActivityDayDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.routeMap.setTileSource(TileSourceFactory.MAPNIK)
        binding.routeMap.setMultiTouchControls(true)
        binding.routeMap.isTilesScaledToDpi = true
        // Avoid a pure-white "hole" while tiles / first camera fit settle.
        binding.routeMap.setBackgroundColor(0xFFE8EEF4.toInt())

        binding.toolbar.title = DayTitle.format(date)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.exportButton.setOnClickListener {
            GpxExportDialog.show(this, store, date)
        }
        binding.noteButton.setOnClickListener {
            DayNoteDialog.show(this, store, dateIso) { refresh() }
        }
        binding.jumpsButton.setOnClickListener {
            JumpCleanupDialog.show(this, store, dateIso) { refresh() }
        }
        binding.addPhotoButton.setOnClickListener {
            pickPhotoLauncher.launch("image/*")
        }
        val photoViews = listOf(binding.dayPhoto1, binding.dayPhoto2, binding.dayPhoto3)
        photoViews.forEach { view ->
            view.setOnClickListener {
                val name = view.tag as? String ?: return@setOnClickListener
                PhotoViewerDialog.show(this, store, photoStore, dateIso, name) { refresh() }
            }
        }
        statsAdapter = DayStatsAdapter(onReordered = {})
        binding.dayStats.adapter = statsAdapter
        statsAdapter.attachTo(binding.dayStats)

        refresh()
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
                DayStatKind.POINT_COUNT to points.size.toString(),
                DayStatKind.MAX_SPEED to if (points.size < 2) emDash else DayTitle.formatSpeed(speed.maxSpeedKmh),
                DayStatKind.AVG_SPEED to if (points.size < 2) emDash else DayTitle.formatSpeed(speed.avgSpeedKmh),
                DayStatKind.ACTIVE_DURATION to if (points.size < 2) {
                    emDash
                } else {
                    DayTitle.formatDuration(speed.activeMillis)
                },
            ),
        )

        if (jumps.isEmpty()) {
            binding.jumpsButton.visibility = View.GONE
        } else {
            binding.jumpsButton.visibility = View.VISIBLE
            binding.jumpsButton.text = getString(R.string.jumps_button, jumps.size)
        }

        binding.noteBadge.visibility = if (record?.note.isNullOrEmpty()) View.GONE else View.VISIBLE

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

    private fun applyDayPhotos(photos: List<String>) {
        val slots = listOf(binding.dayPhoto1, binding.dayPhoto2, binding.dayPhoto3)
        slots.forEachIndexed { i, view ->
            val name = photos.getOrNull(i)
            if (name == null) {
                view.visibility = View.GONE
                view.tag = null
                view.setImageDrawable(null)
                return@forEachIndexed
            }
            view.visibility = View.VISIBLE
            view.tag = name
            val thumb = photoStore.thumbFile(dateIso, name)
            view.setImageBitmap(
                if (thumb.exists()) BitmapFactory.decodeFile(thumb.absolutePath) else null,
            )
        }
        binding.addPhotoButton.visibility =
            if (photos.size >= PhotoStore.MAX_PHOTOS_PER_DAY) View.GONE else View.VISIBLE
    }

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
        private const val EXTRA_DATE_ISO = "date_iso"

        fun intent(context: Context, dateIso: String): Intent =
            Intent(context, DayDetailActivity::class.java).putExtra(EXTRA_DATE_ISO, dateIso)
    }
}
