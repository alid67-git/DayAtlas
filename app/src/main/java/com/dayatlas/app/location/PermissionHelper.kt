package com.dayatlas.app.location

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

object PermissionHelper {
    fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasCoarseLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasLocation(context: Context): Boolean = hasFineLocation(context) || hasCoarseLocation(context)

    fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocation(context)
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java)
        return am.canScheduleExactAlarms()
    }

    fun foregroundLocationPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
}

object Intents {
    const val ACTION_POINT_SAVED = "com.dayatlas.app.POINT_SAVED"
    const val EXTRA_GEOMETRY_CHANGED = "geometry_changed"
    const val EXTRA_DATE_ISO = "date_iso"
    const val EXTRA_POINT_COUNT = "point_count"
    const val EXTRA_DISTANCE_M = "distance_m"
    const val EXTRA_TIME_MILLIS = "time_millis"
    const val EXTRA_LAT = "lat"
    const val EXTRA_LON = "lon"

    fun pointSaved(
        context: Context,
        dateIso: String,
        pointCount: Int,
        distanceMeters: Double,
        timeMillis: Long,
        lat: Double,
        lon: Double,
        geometryChanged: Boolean,
    ): Intent =
        Intent(ACTION_POINT_SAVED)
            .setPackage(context.packageName)
            .putExtra(EXTRA_DATE_ISO, dateIso)
            .putExtra(EXTRA_POINT_COUNT, pointCount)
            .putExtra(EXTRA_DISTANCE_M, distanceMeters)
            .putExtra(EXTRA_TIME_MILLIS, timeMillis)
            .putExtra(EXTRA_LAT, lat)
            .putExtra(EXTRA_LON, lon)
            .putExtra(EXTRA_GEOMETRY_CHANGED, geometryChanged)
}
