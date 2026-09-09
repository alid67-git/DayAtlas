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
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.databinding.ActivityMainBinding
import com.dayatlas.app.export.GpxExportDialog
import com.dayatlas.app.location.Intents
import com.dayatlas.app.location.PermissionHelper
import com.dayatlas.app.location.TrackingController
import com.dayatlas.app.prefs.AppPrefs
import com.dayatlas.app.route.RouteMapController
import com.dayatlas.app.update.UpdateChecker
import com.dayatlas.app.update.UpdateInstaller
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

class MainActivity : DayAtlasActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPrefs
    private val store by lazy { DayStore(this) }
    private var pendingStart = false
    private var askedBatteryThisSession = false
    private var askedExactThisSession = false
    private var mapDate: LocalDate = DayTitle.localToday()

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

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_export_gpx -> {
                    GpxExportDialog.show(this, store, mapDate)
                    true
                }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
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
            .setMessage(BuildInfo.BUILD_NOTE)
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
        binding.routeMap.onResume()
        refresh()
    }

    override fun onPause() {
        binding.routeMap.onPause()
        super.onPause()
    }

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
        binding.distance.text = if (record.points.isEmpty()) {
            getString(R.string.em_dash)
        } else {
            DayTitle.formatDistance(record.distanceMeters)
        }
        binding.lastPoint.text = record.points.lastOrNull()?.let { point ->
            Instant.ofEpochMilli(point.timeMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(TIME_FMT)
        } ?: getString(R.string.em_dash)
        binding.pointCount.text = record.points.size.toString()

        if (prefs.dailyMode) {
            binding.toggle.visibility = View.GONE
            binding.hint.text = getString(R.string.daily_mode_hint)
        } else {
            binding.toggle.visibility = View.VISIBLE
            binding.toggle.setText(if (prefs.trackingEnabled) R.string.stop else R.string.start)
            binding.hint.text = getString(R.string.manual_hint)
        }

        refreshMap()
    }

    private fun refreshMap() {
        val today = DayTitle.localToday()
        binding.mapDayTitle.text = DayTitle.format(mapDate)
        binding.nextDay.isEnabled = mapDate < today
        binding.goToday.visibility = if (mapDate == today) View.GONE else View.VISIBLE
        val points = store.load(DayTitle.iso(mapDate))?.points.orEmpty()
        RouteMapController.show(binding.routeMap, this, points, binding.emptyState)
    }

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }
}
