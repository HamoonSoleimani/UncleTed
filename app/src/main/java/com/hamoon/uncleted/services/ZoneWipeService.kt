package com.hamoon.uncleted.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.hamoon.uncleted.util.NotificationHelper
import com.hamoon.uncleted.util.PermissionUtils
import com.hamoon.uncleted.util.PolygonUtils

class ZoneWipeService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    companion object {
        private const val TAG = "ZoneWipeService"
        private const val UPDATE_INTERVAL_MS = 5000L // Check every 5 seconds
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Start Foreground immediately to ensure system doesn't kill it
        startForeground(3003, NotificationHelper.createBasicNotification(this))

        startLocationMonitoring()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationMonitoring() {
        if (!PermissionUtils.hasLocationPermissions(this)) {
            Log.e(TAG, "Missing location permissions. Zone Wipe disabled.")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(5f) // Update if moved 5 meters
            .setWaitForAccurateLocation(true)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // Check if inside Evin Prison
                    if (PolygonUtils.isLocationInZone(location, PolygonUtils.EVIN_PRISON_PERIMETER)) {
                        Log.e(TAG, "!!! DEVICE ENTERED NO-GO ZONE (EVIN) !!!")
                        Log.e(TAG, "!!! INITIATING GEOGRAPHIC SUICIDE !!!")

                        // Trigger Critical Wipe
                        PanicActionService.trigger(
                            this@ZoneWipeService,
                            "GEOFENCE_SUICIDE_EVIN",
                            PanicActionService.Severity.CRITICAL
                        )

                        // Stop service to prevent multiple triggers (though panic service handles cooldown)
                        fusedLocationClient.removeLocationUpdates(this)
                        stopSelf()
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        Log.i(TAG, "Zone Wipe Service Armed. Monitoring Evin perimeter.")
    }

    override fun onDestroy() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}