package com.dayatlas.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.dayatlas.app.data.DayRecord
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.data.JumpCleanupDialog
import com.dayatlas.app.data.JumpFilter
import com.dayatlas.app.data.RangeStats
import com.dayatlas.app.data.SpeedStats
import com.dayatlas.app.data.TrackPoint
import com.dayatlas.app.databinding.ActivityMainBinding
import com.dayatlas.app.export.GpxExportDialog
import com.dayatlas.app.location.Intents
import com.dayatlas.app.location.PermissionHelper
import com.dayatlas.app.location.TrackingController
import com.dayatlas.app.prefs.AppPrefs
import com.dayatlas.app.route.DayStatKind
import com.dayatlas.app.route.DayStatsAdapter
import com.dayatlas.app.route.RouteDayRow
import com.dayatlas.app.route.RouteMapController
import com.dayatlas.app.route.RoutesAdapter
import com.dayatlas.app.stats.StatsRange
import com.dayatlas.app.update.UpdateChecker
import com.dayatlas.app.update.UpdateInstaller
import androidx.recyclerview.widget.LinearLayoutManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import android.view.LayoutInflater
import android.widget.TextView

class MainActivity : DayAtlasActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPrefs
    private val store by lazy { DayStore(this) }
    private val dayStatsAdapter = DayStatsAdapter { newOrder ->
        persistDayStatsOrder(newOrder)
    }
    private val routesAdapter = RoutesAdapter(
        onOpen = { date -> openRouteOnMap(date) },
        onExport = { date -> GpxExportDialog.show(this, store, date) },
    )
    private var pendingStart = false
    private var askedBatteryThisSession = false
    private var askedExactThisSession = false
    private var mapDate: LocalDate = DayTitle.localToday()
    private var statsRange: StatsRange = StatsRange.LAST_7

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            continuePermissionChain()
        } else {
            pendingStart = false
            Toast.makeText(this, R.string.need_location, Toast.LENGTH_LONG).show()
            refresh()
        }
    }

    private val backgroundLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { continuePermissionChain() }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { continuePermissionChain() }

    private val pointReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPrefs(this)

        binding.routeMap.setTileSource(TileSourceFactory.MAPNIK)
        binding.routeMap.setMultiTouchControls(true)

        binding.todayStats.adapter = dayStatsAdapter
        dayStatsAdapter.attachTo(binding.todayStats)

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        binding.bottomNav.selectedItemId = R.id.nav_daily

        binding.moreSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.moreHelp.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        binding.moreVersion.text = getString(R.string.current_version, BuildConfig.VERSION_NAME)

        setupStatsRangeChips()
        binding.routesList.layoutManager = LinearLayoutManager(this)
        binding.routesList.adapter = routesAdapter
        binding.routesExportRange.setOnClickListener {
            GpxExportDialog.show(this, store, mapDate)
        }

        binding.previousDay.setOnClickListener {
            mapDate = mapDate.minusDays(1)
            refreshMap()
        }
        binding.nextDay.setOnClickListener {
            if (mapDate < DayTitle.localToday()) {
                mapDate = mapDate.plusDays(1)
                refreshMap()
            }
        }
        binding.goToday.setOnClickListener {
            mapDate = DayTitle.localToday()
            refreshMap()
        }

        binding.jumpsButton.setOnClickListener {
            JumpCleanupDialog.show(
                this,
                store,
                DayTitle.iso(mapDate),
            ) { refresh() }
        }

        binding.toggle.setOnClickListener {
            if (prefs.trackingEnabled) {
                TrackingController.stop(this, prefs)
                refresh()
            } else {
                ensurePermissionsThenStart()
            }
        }

        if (prefs.dailyMode) {
            // Günlük mod: onay diyaloğu yok; yalnızca sistem izinleri.
            ensurePermissionsThenStart()
        }

        maybeShowChangelog()
        checkForUpdate()
    }

    private fun maybeShowChangelog() {
        if (prefs.lastSeenBuildNoteVersion == BuildConfig.VERSION_NAME) return
        prefs.lastSeenBuildNoteVersion = BuildConfig.VERSION_NAME
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.changelog_title, BuildConfig.VERSION_NAME))
            .setMessage(getString(R.string.build_note))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /**
     * Best-effort background check; skipped for debug builds. Uses
     * applicationContext so RecentsHider finishing this activity does not
     * cancel the download. Install UI (if needed) is surfaced via notification
     * when the activity is no longer in the foreground.
     */
    private fun checkForUpdate() {
        if (BuildConfig.DEBUG) return
        UpdateChecker.check(BuildConfig.VERSION_NAME) { info ->
            if (info == null) return@check
            UpdateInstaller.download(applicationContext, info, silent = true)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            pointReceiver,
            IntentFilter(Intents.ACTION_POINT_SAVED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    override fun onStop() {
        runCatching { unregisterReceiver(pointReceiver) }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (binding.paneDaily.visibility == View.VISIBLE) {
            binding.routeMap.onResume()
        }
        UpdateInstaller.resumePending(this, offerUi = true)
        refresh()
    }

    override fun onPause() {
        binding.routeMap.onPause()
        super.onPause()
    }

    private fun showTab(itemId: Int) {
        val daily = itemId == R.id.nav_daily
        binding.paneDaily.visibility = if (daily) View.VISIBLE else View.GONE
        binding.paneStats.visibility = if (itemId == R.id.nav_stats) View.VISIBLE else View.GONE
        binding.paneRoutes.visibility = if (itemId == R.id.nav_routes) View.VISIBLE else View.GONE
        binding.paneMore.visibility = if (itemId == R.id.nav_more) View.VISIBLE else View.GONE
        when (itemId) {
            R.id.nav_daily -> {
                binding.routeMap.onResume()
                refreshMap()
            }
            R.id.nav_stats -> {
                binding.routeMap.onPause()
                refreshStats()
            }
            R.id.nav_routes -> {
                binding.routeMap.onPause()
                refreshRoutes()
            }
            else -> binding.routeMap.onPause()
        }
    }

    private fun refreshRoutes() {
        val rows = store.listDates().asReversed().mapNotNull { date ->
            val record = store.load(DayTitle.iso(date)) ?: return@mapNotNull null
            if (record.points.isEmpty()) return@mapNotNull null
            val speed = SpeedStats.compute(record.points)
            val meta = if (speed.activeMillis > 0) {
                getString(
                    R.string.routes_day_meta,
                    DayTitle.formatDistance(record.distanceMeters),
                    record.points.size,
                    DayTitle.formatDuration(speed.activeMillis),
                )
            } else {
                getString(
                    R.string.routes_day_meta_short,
                    DayTitle.formatDistance(record.distanceMeters),
                    record.points.size,
                )
            }
            RouteDayRow(
                date = date,
                title = DayTitle.format(date),
                meta = meta,
            )
        }
        routesAdapter.submit(rows)
        binding.routesEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        binding.routesList.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openRouteOnMap(date: LocalDate) {
        mapDate = date
        binding.bottomNav.selectedItemId = R.id.nav_daily
        // showTab runs via the selected-item listener.
    }

    private fun setupStatsRangeChips() {
        binding.chipRange7.isChecked = true
        binding.statsRangeChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            statsRange = when (id) {
                R.id.chipRangeToday -> StatsRange.TODAY
                R.id.chipRange7 -> StatsRange.LAST_7
                R.id.chipRange30 -> StatsRange.LAST_30
                R.id.chipRangeMonth -> StatsRange.THIS_MONTH
                R.id.chipRangeAll -> StatsRange.ALL
                else -> return@setOnCheckedStateChangeListener
            }
            refreshStats()
        }
    }

    private fun refreshStats() {
        val today = DayTitle.localToday()
        val dates = store.listDates()
        val bounds = statsRange.bounds(today, dates.firstOrNull())
        if (bounds == null) {
            bindStatsSummary(RangeStats.Summary.EMPTY, rangeLabel = null)
            return
        }
        val (from, to) = bounds
        val records = store.loadRange(from, to)
        val label = if (from == to) {
            getString(R.string.stats_range_label_single, formatStatsDay(from))
        } else {
            getString(
                R.string.stats_range_label,
                formatStatsDay(from),
                formatStatsDay(to),
            )
        }
        bindStatsSummary(RangeStats.summarize(records), label)
    }

    private fun bindStatsSummary(summary: RangeStats.Summary, rangeLabel: String?) {
        binding.statsRangeLabel.text = rangeLabel.orEmpty()
        val empty = summary.dayCount == 0
        binding.statsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.statsTotalDistance.text =
            if (empty) getString(R.string.em_dash) else DayTitle.formatDistance(summary.totalDistanceMeters)
        binding.statsActiveDays.text =
            if (empty) getString(R.string.em_dash) else summary.dayCount.toString()
        binding.statsMaxSpeed.text =
            if (empty || summary.maxSpeedKmh <= 0) {
                getString(R.string.em_dash)
            } else {
                DayTitle.formatSpeed(summary.maxSpeedKmh)
            }
        binding.statsActiveDuration.text =
            if (empty || summary.activeMillis <= 0) {
                getString(R.string.em_dash)
            } else {
                DayTitle.formatDuration(summary.activeMillis)
            }
        binding.statsAvgSpeed.text =
            if (empty || summary.avgSpeedKmh <= 0) {
                getString(R.string.em_dash)
            } else {
                DayTitle.formatSpeed(summary.avgSpeedKmh)
            }
        binding.statsPointsBest.text = when {
            empty -> getString(R.string.em_dash)
            summary.bestDayIso != null -> getString(
                R.string.stats_points_and_best,
                summary.totalPoints,
                formatStatsDay(LocalDate.parse(summary.bestDayIso)),
                DayTitle.formatDistance(summary.bestDayDistanceMeters),
            )
            else -> getString(R.string.stats_points_only, summary.totalPoints)
        }
        renderDailyBars(summary)
    }

    private fun renderDailyBars(summary: RangeStats.Summary) {
        val container = binding.statsDailyBars
        container.removeAllViews()
        if (summary.dailyDistances.isEmpty()) return
        val maxMeters = summary.dailyDistances.maxOf { it.second }.coerceAtLeast(1.0)
        // Keep the strip readable when "All" spans many months.
        val rows = summary.dailyDistances.takeLast(60)
        val inflater = LayoutInflater.from(this)
        for ((iso, meters) in rows) {
            val row = inflater.inflate(R.layout.item_stats_day_bar, container, false)
            row.findViewById<TextView>(R.id.barDayLabel).text =
                formatStatsDay(LocalDate.parse(iso))
            row.findViewById<TextView>(R.id.barDayValue).text =
                if (meters <= 0) getString(R.string.em_dash) else DayTitle.formatDistance(meters)
            val fill = row.findViewById<View>(R.id.barFill)
            fill.post {
                val trackWidth = (fill.parent as View).width
                val lp = fill.layoutParams
                lp.width = ((meters / maxMeters) * trackWidth).toInt().coerceAtLeast(if (meters > 0) 4 else 0)
                fill.layoutParams = lp
            }
            container.addView(row)
        }
    }

    private fun formatStatsDay(date: LocalDate): String = DayTitle.formatShort(date)

    private fun ensurePermissionsThenStart() {
        pendingStart = true
        continuePermissionChain()
    }

    private fun continuePermissionChain() {
        if (!PermissionHelper.hasLocation(this)) {
            // Permission UI is not started via startActivity, so mark retain
            // explicitly - otherwise RecentsHider would tear the task down
            // while the system dialog is up.
            RecentsHider.retainForExternalNavigation()
            locationLauncher.launch(PermissionHelper.foregroundLocationPermissions())
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !PermissionHelper.hasBackgroundLocation(this)
        ) {
            RecentsHider.retainForExternalNavigation()
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotifications(this)
        ) {
            RecentsHider.retainForExternalNavigation()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        maybeRequestBatteryExemption()
        maybeRequestExactAlarms()
        if (pendingStart || prefs.dailyMode) {
            TrackingController.start(this, prefs, sampleSoon = true)
        }
        pendingStart = false
        refresh()
    }

    private fun maybeRequestBatteryExemption() {
        if (askedBatteryThisSession) return
        if (PermissionHelper.isIgnoringBatteryOptimizations(this)) return
        askedBatteryThisSession = true
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    private fun maybeRequestExactAlarms() {
        if (askedExactThisSession) return
        if (PermissionHelper.canScheduleExactAlarms(this)) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        askedExactThisSession = true
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    private fun refresh() {
        val today = DayTitle.localToday()
        if (mapDate.isAfter(today)) mapDate = today

        // Top block always mirrors the live "today" record — day carousel
        // below must not rewrite this title / cards.
        val record = store.loadToday()
        binding.dayTitle.text = record.title
        val recording = prefs.trackingEnabled || prefs.dailyMode
        binding.status.text = when {
            prefs.dailyMode -> getString(R.string.status_daily)
            recording -> getString(R.string.status_recording)
            else -> getString(R.string.status_stopped)
        }
        binding.status.setTextColor(
            ContextCompat.getColor(
                this,
                if (recording) R.color.status_on else R.color.status_off,
            ),
        )
        if (prefs.dailyMode) {
            binding.toggle.visibility = View.GONE
            binding.hint.visibility = View.GONE
        } else {
            binding.toggle.visibility = View.VISIBLE
            binding.toggle.setText(if (prefs.trackingEnabled) R.string.stop else R.string.start)
            binding.hint.visibility = View.VISIBLE
            binding.hint.text = getString(R.string.manual_hint)
        }

        val todayPoints = record.points
        val todayValues = dayStatValues(record, todayPoints)
        val hidden = prefs.dayStatsHidden
        val order = DayStatKind.parseOrder(prefs.dayStatsOrderRaw)
        dayStatsAdapter.submit(
            order.filter { it.key !in hidden }
                .map { it to (todayValues[it] ?: getString(R.string.em_dash)) },
        )

        refreshMap()
    }

    private fun refreshMap() {
        val today = DayTitle.localToday()
        val dayLabel = DayTitle.format(mapDate)
        binding.mapDayTitle.text = dayLabel
        binding.nextDay.isEnabled = mapDate < today
        binding.goToday.visibility = if (mapDate == today) View.GONE else View.VISIBLE
        val dateIso = DayTitle.iso(mapDate)
        val record = store.load(dateIso)
        val points = record?.points.orEmpty()
        val emDash = getString(R.string.em_dash)
        binding.mapDistance.text = if (points.isEmpty()) {
            emDash
        } else {
            DayTitle.formatDistance(record?.distanceMeters ?: 0.0)
        }
        binding.mapLastPoint.text = points.lastOrNull()?.let { point ->
            Instant.ofEpochMilli(point.timeMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(TIME_FMT)
        } ?: emDash
        binding.mapPointCount.text = points.size.toString()

        val jumps = JumpFilter.findJumps(points)
        if (jumps.isEmpty()) {
            binding.jumpsButton.visibility = View.GONE
        } else {
            binding.jumpsButton.visibility = View.VISIBLE
            binding.jumpsButton.text = getString(R.string.jumps_button, jumps.size)
        }

        RouteMapController.show(
            map = binding.routeMap,
            context = this,
            points = points,
            emptyState = binding.emptyState,
            onJumpTap = { jump ->
                JumpCleanupDialog.confirmDelete(
                    this,
                    store,
                    dateIso,
                    jump,
                ) { refresh() }
            },
        )
    }

    private fun dayStatValues(
        record: DayRecord?,
        points: List<TrackPoint>,
    ): Map<DayStatKind, String> {
        val emDash = getString(R.string.em_dash)
        val speed = SpeedStats.compute(points)
        return mapOf(
            DayStatKind.DISTANCE to if (points.isEmpty()) {
                emDash
            } else {
                DayTitle.formatDistance(record?.distanceMeters ?: 0.0)
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
            DayStatKind.GPS_INTERVAL to formatGpsInterval(prefs.effectiveIntervalSeconds),
            DayStatKind.MAX_SPEED to if (points.size < 2) emDash else DayTitle.formatSpeed(speed.maxSpeedKmh),
            DayStatKind.AVG_SPEED to if (points.size < 2) emDash else DayTitle.formatSpeed(speed.avgSpeedKmh),
            DayStatKind.ACTIVE_DURATION to if (points.size < 2) {
                emDash
            } else {
                DayTitle.formatDuration(speed.activeMillis)
            },
        )
    }

    private fun formatGpsInterval(seconds: Int): String = when (seconds) {
        30 -> getString(R.string.interval_30s_short)
        60 -> getString(R.string.interval_1_short)
        180 -> getString(R.string.interval_3_short)
        300 -> getString(R.string.interval_5_short)
        else -> getString(R.string.interval_seconds_short, seconds)
    }

    /**
     * Persist the full visible order from the grid; hidden kinds keep their
     * previous relative order and are appended after.
     */
    private fun persistDayStatsOrder(newVisible: List<DayStatKind>) {
        val hidden = prefs.dayStatsHidden
        val oldOrder = DayStatKind.parseOrder(prefs.dayStatsOrderRaw)
        val hiddenOrdered = oldOrder.filter { it.key in hidden }
        prefs.dayStatsOrderRaw = DayStatKind.joinOrder(newVisible + hiddenOrdered)
    }

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }
}
