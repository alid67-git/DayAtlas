package com.dayatlas.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.location.PermissionHelper
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.databinding.ActivitySettingsBinding
import com.dayatlas.app.location.TrackingController
import com.dayatlas.app.prefs.AppPrefs

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPrefs

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

        when (prefs.intervalMinutes) {
            3 -> binding.interval3.isChecked = true
            4 -> binding.interval4.isChecked = true
            else -> binding.interval5.isChecked = true
        }
        binding.intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            val minutes = when (checkedId) {
                R.id.interval3 -> 3
                R.id.interval4 -> 4
                else -> 5
            }
            prefs.intervalMinutes = minutes
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

        val today = DayTitle.iso(DayTitle.localToday())
        val dir = DayStore(this).daysDir().absolutePath
        binding.filesHint.text = getString(R.string.today_files) + "\n$dir\n$today.json / $today.gpx"
    }
}
