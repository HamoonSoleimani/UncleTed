package com.hamoon.uncleted.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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
        private const val NOTIFICATION_ID = 3003
        private const val UPDATE_INTERVAL_MS = 5000L
    }

    override fun onCreate() {
        super.onCreate()

        if (!PermissionUtils.hasLocationPermissions(this)) {
            Log.e(TAG, "Location permissions missing. Cannot start ZoneWipeService.")
            stopSelf()
            return
        }

        val notification = NotificationHelper.createBasicNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ZoneWipeService in foreground", e)
            stopSelf()
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
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
            .setMinUpdateDistanceMeters(5f)
            .setWaitForAccurateLocation(true)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (PolygonUtils.isLocationInZone(location, PolygonUtils.EVIN_PRISON_PERIMETER)) {
                        Log.e(TAG, "!!! DEVICE ENTERED NO-GO ZONE (EVIN) !!!")
                        Log.e(TAG, "!!! INITIATING GEOGRAPHIC SUICIDE !!!")

                        PanicActionService.trigger(
                            this@ZoneWipeService,
                            "GEOFENCE_SUICIDE_EVIN",
                            PanicActionService.Severity.CRITICAL
                        )

                        fusedLocationClient.removeLocationUpdates(this)
                        stopSelf()
                        break
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