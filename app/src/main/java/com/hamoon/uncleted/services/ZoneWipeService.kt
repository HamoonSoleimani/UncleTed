package com.hamoon.uncleted.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.NotificationHelper
import com.hamoon.uncleted.util.PermissionUtils
import com.hamoon.uncleted.util.PolygonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ZoneWipeService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var consecutiveBreachCount = 0

    companion object {
        private const val TAG = "ZoneWipeService"
        private const val NOTIFICATION_ID = 3003
        private const val UPDATE_INTERVAL_MS = 5000L
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 30.0f
        private const val REQUIRED_CONSECUTIVE_BREACHES = 3
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
            Log.e(TAG, "Failed starting ZoneWipeService in foreground", e)
            stopSelf()
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startLocationMonitoring()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationMonitoring() {
        if (!PermissionUtils.hasLocationPermissions(this)) {
            Log.e(TAG, "Missing location permissions. Zone Wipe monitoring aborted.")
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
                    processLocationSample(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        Log.i(TAG, "Zone Wipe Service Armed with multi-zone support.")
        EventLogger.log(this, "ZONE WIPE: Multi-zone perimeter monitoring active.")
    }

    private fun processLocationSample(location: Location?) {
        if (location == null) return

        if (!location.hasAccuracy() || location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            Log.w(TAG, "GPS accuracy insufficient (${location.accuracy}m > ${MAX_ACCEPTABLE_ACCURACY_METERS}m). Ignoring fix.")
            return
        }

        val activeZones = mutableListOf<PolygonUtils.WipeZone>()

        // 1. Built-in Evin Prison perimeter
        if (SecurityPreferences.isGeofenceSuicideEnabled(this)) {
            activeZones.add(
                PolygonUtils.WipeZone(
                    id = "builtin_evin",
                    name = "Evin Prison Perimeter",
                    polygon = PolygonUtils.EVIN_PRISON_PERIMETER,
                    isEnabled = true
                )
            )
        }

        // 2. Custom user-defined destruction zones
        activeZones.addAll(SecurityPreferences.getCustomWipeZones(this).filter { it.isEnabled })

        var breachedZoneName: String? = null
        for (zone in activeZones) {
            if (PolygonUtils.isLocationInWipeZone(location, zone)) {
                breachedZoneName = zone.name
                break
            }
        }

        if (breachedZoneName != null) {
            consecutiveBreachCount++
            Log.e(TAG, "DESTRUCTION ZONE BREACH: '$breachedZoneName' [$consecutiveBreachCount/$REQUIRED_CONSECUTIVE_BREACHES]")

            if (consecutiveBreachCount >= REQUIRED_CONSECUTIVE_BREACHES) {
                Log.e(TAG, "!!! CONFIRMED DEVICE INSIDE DESTRUCTION ZONE: '$breachedZoneName' !!!")
                Log.e(TAG, "!!! INITIATING IMMEDIATE GEOGRAPHIC SUICIDE !!!")
                EventLogger.log(this, "CRITICAL: Confirmed breach of destruction zone '$breachedZoneName'. Initiating wipe.")

                fusedLocationClient.removeLocationUpdates(locationCallback)

                CoroutineScope(Dispatchers.IO).launch {
                    val strategy = DefenseCoordinator.resolveStrategy(this@ZoneWipeService)
                    strategy.executeWipe("GEOFENCE_SUICIDE_$breachedZoneName")
                    PanicActionService.trigger(
                        this@ZoneWipeService,
                        "GEOFENCE_SUICIDE_EVIN",
                        PanicActionService.Severity.CRITICAL
                    )
                }
                stopSelf()
            }
        } else {
            consecutiveBreachCount = 0
        }
    }

    override fun onDestroy() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}