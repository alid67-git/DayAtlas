package com.dayatlas.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.dayatlas.app.backup.DriveFolderBackup
import com.dayatlas.app.boot.OemAutostart
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.databinding.ActivitySettingsBinding
import com.dayatlas.app.location.PermissionHelper
import com.dayatlas.app.location.TrackingController
import com.dayatlas.app.prefs.AppPrefs
import com.dayatlas.app.route.DayStatKind
import com.dayatlas.app.update.UpdateChecker
import com.dayatlas.app.update.UpdateInstaller

class SettingsActivity : DayAtlasActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPrefs

    private val pickDriveFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        RecentsHider.retainForExternalNavigation()
        DriveFolderBackup.takeFolder(this, prefs, uri)
        refreshDriveUi()
        if (prefs.driveBackupEnabled) {
            DriveFolderBackup.runNow(this) { result ->
                toastBackupResult(result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPrefs(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.helpButton.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        binding.dailyMode.isChecked = prefs.dailyMode
        binding.dailyMode.setOnCheckedChangeListener { _, checked ->
            if (checked && !PermissionHelper.hasLocation(this)) {
                Toast.makeText(this, R.string.need_location, Toast.LENGTH_LONG).show()
            }
            TrackingController.setDailyMode(this, checked, prefs)
        }

        setupLanguagePicker()
        setupIntervalPicker()
        binding.gpsRateLabel.text =
            getString(R.string.gps_check_rate, formatIntervalSeconds(prefs.intervalSeconds))

        setupDayStatsCheckboxes()
        setupExpandable(binding.dayStatsHeader, binding.dayStatsChevron, binding.dayStatsContent)
        setupExpandable(binding.permissionsHeader, binding.permissionsChevron, binding.permissionsContent)

        binding.locationSettings.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                },
            )
        }
        binding.batteryExemption.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            runCatching { startActivity(intent) }
                .onFailure {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
        }
        binding.batterySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        binding.alarmSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            runCatching { startActivity(intent) }
        }

        binding.autostartSettings.visibility = if (OemAutostart.isKnownOem()) View.VISIBLE else View.GONE
        binding.autostartSettings.setOnClickListener {
            if (!OemAutostart.open(this)) {
                Toast.makeText(this, R.string.autostart_not_found, Toast.LENGTH_LONG).show()
            }
        }

        val today = DayTitle.iso(DayTitle.localToday())
        val dir = DayStore(this).daysDir().absolutePath
        binding.filesHint.text = getString(R.string.today_files) + "\n$dir\n$today.json / $today.gpx"

        binding.driveBackup.isChecked = prefs.driveBackupEnabled
        binding.driveBackup.setOnCheckedChangeListener { _, checked ->
            if (checked && !DriveFolderBackup.hasFolder(prefs)) {
                binding.driveBackup.isChecked = false
                prefs.driveBackupEnabled = false
                Toast.makeText(this, R.string.drive_need_folder, Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            prefs.driveBackupEnabled = checked
            if (checked) {
                DriveFolderBackup.runNow(this) { toastBackupResult(it) }
            }
        }
        binding.drivePickFolder.setOnClickListener {
            RecentsHider.retainForExternalNavigation()
            pickDriveFolder.launch(null)
        }
        binding.driveBackupNow.setOnClickListener {
            if (!DriveFolderBackup.hasFolder(prefs)) {
                Toast.makeText(this, R.string.drive_need_folder, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Toast.makeText(this, R.string.drive_backup_now, Toast.LENGTH_SHORT).show()
            DriveFolderBackup.runNow(this) { toastBackupResult(it) }
        }
        binding.driveRestoreNow.setOnClickListener {
            if (!DriveFolderBackup.hasFolder(prefs)) {
                Toast.makeText(this, R.string.drive_need_folder, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.drive_restore_confirm_title)
                .setMessage(R.string.drive_restore_confirm_message)
                .setPositiveButton(R.string.drive_restore_now) { _, _ ->
                    DriveFolderBackup.restoreNow(this) { toastRestoreResult(it) }
                }
                .setNegativeButton(R.string.export_cancel, null)
                .show()
        }
        refreshDriveUi()

        binding.versionLabel.text = getString(R.string.current_version, BuildConfig.VERSION_NAME)
        if (BuildConfig.SELF_UPDATE_ENABLED) {
            binding.checkUpdates.setOnClickListener {
                Toast.makeText(this, R.string.checking_for_updates, Toast.LENGTH_SHORT).show()
                UpdateChecker.check(BuildConfig.VERSION_NAME) { info ->
                    if (isFinishing) return@check
                    if (info == null) {
                        Toast.makeText(this, R.string.up_to_date, Toast.LENGTH_SHORT).show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle(R.string.update_available_title)
                            .setMessage(getString(R.string.update_available_message, info.version))
                            .setPositiveButton(R.string.update_download) { _, _ ->
                                UpdateInstaller.download(this, info)
                            }
                            .setNegativeButton(R.string.update_later, null)
                            .show()
                    }
                }
            }
        } else {
            // Play build: updates come from the Play Store itself, not the
            // GitHub-release self-updater - hide the manual check button
            // rather than leave a control that would silently do nothing.
            binding.checkUpdates.visibility = View.GONE
        }
    }

    private fun setupLanguagePicker() {
        val tags = listOf(AppLocale.SYSTEM) + AppLocale.SUPPORTED
        binding.languageDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, tags.map(::languageLabel)),
        )
        binding.languageDropdown.setText(languageLabel(prefs.appLanguage), false)
        binding.languageDropdown.setOnItemClickListener { _, _, position, _ ->
            val tag = tags[position]
            if (tag == prefs.appLanguage) return@setOnItemClickListener
            prefs.appLanguage = tag
            // AppCompatDelegate recreates every active activity on its own
            // once the locale change lands - an extra recreate() call here
            // used to race with that and was why the wrong language could
            // end up shown as selected after switching.
            AppLocale.apply(tag)
        }
    }

    private fun languageLabel(tag: String): String = when (tag) {
        AppLocale.TR -> getString(R.string.language_tr)
        AppLocale.EN -> getString(R.string.language_en)
        AppLocale.DE -> getString(R.string.language_de)
        AppLocale.ZH -> getString(R.string.language_zh)
        AppLocale.HI -> getString(R.string.language_hi)
        AppLocale.ES -> getString(R.string.language_es)
        AppLocale.FR -> getString(R.string.language_fr)
        AppLocale.AR -> getString(R.string.language_ar)
        else -> getString(R.string.language_system)
    }

    private fun setupIntervalPicker() {
        val seconds = intArrayOf(30, 60, 180, 300)
        binding.intervalDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, seconds.map(::formatIntervalSeconds)),
        )
        binding.intervalDropdown.setText(formatIntervalSeconds(prefs.intervalSeconds), false)
        binding.intervalDropdown.setOnItemClickListener { _, _, position, _ ->
            val newSeconds = seconds[position]
            if (newSeconds == prefs.intervalSeconds) return@setOnItemClickListener
            prefs.intervalSeconds = newSeconds
            prefs.resetStationaryBackoff()
            if (prefs.trackingEnabled || prefs.dailyMode) {
                TrackingController.start(this, prefs, sampleSoon = false)
            }
            binding.gpsRateLabel.text = getString(R.string.gps_check_rate, formatIntervalSeconds(newSeconds))
        }
    }

    private fun formatIntervalSeconds(seconds: Int): String = when (seconds) {
        30 -> getString(R.string.interval_30s)
        60 -> getString(R.string.interval_1)
        180 -> getString(R.string.interval_3)
        else -> getString(R.string.interval_5)
    }

    private fun setupExpandable(header: View, chevron: View, content: View) {
        header.setOnClickListener {
            val expand = content.visibility != View.VISIBLE
            content.visibility = if (expand) View.VISIBLE else View.GONE
            chevron.animate().rotation(if (expand) 90f else 0f).setDuration(150).start()
        }
    }

    private fun setupDayStatsCheckboxes() {
        val checkboxes = mapOf(
            DayStatKind.DISTANCE to binding.statCheckDistance,
            DayStatKind.LAST_POINT to binding.statCheckLastPoint,
            DayStatKind.POINT_COUNT to binding.statCheckPointCount,
            DayStatKind.GPS_INTERVAL to binding.statCheckGpsInterval,
            DayStatKind.MAX_SPEED to binding.statCheckMaxSpeed,
            DayStatKind.AVG_SPEED to binding.statCheckAvgSpeed,
            DayStatKind.ACTIVE_DURATION to binding.statCheckActiveDuration,
        )
        val hidden = prefs.dayStatsHidden
        checkboxes.forEach { (kind, checkBox: CheckBox) ->
            checkBox.isChecked = kind.key !in hidden
            checkBox.setOnCheckedChangeListener { _, checked ->
                val current = prefs.dayStatsHidden.toMutableSet()
                if (checked) current.remove(kind.key) else current.add(kind.key)
                prefs.dayStatsHidden = current
            }
        }
    }

    private fun refreshDriveUi() {
        val name = DriveFolderBackup.folderSummary(this, prefs)
        binding.driveFolderLabel.text = if (name == null) {
            getString(R.string.drive_folder_none)
        } else {
            getString(R.string.drive_folder_selected, name)
        }
        val last = prefs.lastDriveBackupDay
        if (last != null) {
            binding.driveFolderLabel.append("\n" + getString(R.string.drive_last_backup, last))
        }
    }

    private fun toastBackupResult(result: DriveFolderBackup.Result) {
        when {
            result.message == "busy" ->
                Toast.makeText(this, R.string.drive_backup_busy, Toast.LENGTH_SHORT).show()
            result.ok -> {
                Toast.makeText(
                    this,
                    getString(R.string.drive_backup_ok, result.uploaded),
                    Toast.LENGTH_SHORT,
                ).show()
                refreshDriveUi()
            }
            else ->
                Toast.makeText(
                    this,
                    getString(R.string.drive_backup_failed, result.message ?: "error"),
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

    private fun toastRestoreResult(result: DriveFolderBackup.RestoreResult) {
        when {
            result.message == "busy" ->
                Toast.makeText(this, R.string.drive_restore_busy, Toast.LENGTH_SHORT).show()
            result.ok && result.restored == 0 ->
                Toast.makeText(this, R.string.drive_restore_none, Toast.LENGTH_SHORT).show()
            result.ok ->
                Toast.makeText(
                    this,
                    getString(R.string.drive_restore_ok, result.restored),
                    Toast.LENGTH_SHORT,
                ).show()
            else ->
                Toast.makeText(
                    this,
                    getString(R.string.drive_restore_failed, result.message ?: "error"),
                    Toast.LENGTH_LONG,
                ).show()
        }
    }
}
