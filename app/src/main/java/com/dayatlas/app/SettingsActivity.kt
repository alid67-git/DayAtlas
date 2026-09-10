package com.dayatlas.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
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

        binding.dailyMode.isChecked = prefs.dailyMode
        binding.dailyMode.setOnCheckedChangeListener { _, checked ->
            if (checked && !PermissionHelper.hasLocation(this)) {
                Toast.makeText(this, R.string.need_location, Toast.LENGTH_LONG).show()
            }
            TrackingController.setDailyMode(this, checked, prefs)
        }

        when (prefs.intervalSeconds) {
            30 -> binding.interval30s.isChecked = true
            60 -> binding.interval1.isChecked = true
            180 -> binding.interval3.isChecked = true
            else -> binding.interval5.isChecked = true
        }
        binding.intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            val seconds = when (checkedId) {
                R.id.interval30s -> 30
                R.id.interval1 -> 60
                R.id.interval3 -> 180
                else -> 300
            }
            prefs.intervalSeconds = seconds
            prefs.resetStationaryBackoff()
            if (prefs.trackingEnabled || prefs.dailyMode) {
                TrackingController.start(this, prefs, sampleSoon = false)
            }
        }

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
        refreshDriveUi()

        binding.versionLabel.text = getString(R.string.current_version, BuildConfig.VERSION_NAME)
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
            binding.driveFolderLabel.append("\nSon yedek: $last")
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
}
