package com.hamoon.uncleted.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.hamoon.uncleted.receivers.GeofenceBroadcastReceiver

object GeofenceHelper {

    private const val TAG = "GeofenceHelper"
    private const val GEOFENCE_ID = "UNCLE_TED_SAFE_ZONE"
    private const val GEOFENCE_RADIUS_METERS = 100f

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(appContext, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(appContext, 0, intent, flags)
    }

    @SuppressLint("MissingPermission")
    fun addGeofence(lat: Double, lon: Double) {
        if (!::appContext.isInitialized) {
            Log.e(TAG, "GeofenceHelper is not initialized with context.")
            return
        }

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot add safe zone geofence: ACCESS_FINE_LOCATION permission missing.")
            return
        }

        val geofencingClient = LocationServices.getGeofencingClient(appContext)

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(lat, lon, GEOFENCE_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        try {
            geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent()).run {
                addOnSuccessListener {
                    Log.i(TAG, "Safe zone geofence registered at ($lat, $lon, radius ${GEOFENCE_RADIUS_METERS}m).")
                    EventLogger.log(appContext, "GEOFENCE: Safe zone geofence established at $lat, $lon.")
                }
                addOnFailureListener { e ->
                    Log.e(TAG, "Failed adding safe zone geofence", e)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during addGeofences call", e)
        }
    }

    fun removeGeofence() {
        if (!::appContext.isInitialized) return

        val geofencingClient = LocationServices.getGeofencingClient(appContext)
        geofencingClient.removeGeofences(getGeofencePendingIntent()).run {
            addOnSuccessListener {
                Log.i(TAG, "Safe zone geofence removed successfully.")
                EventLogger.log(appContext, "GEOFENCE: Safe zone removed.")
            }
            addOnFailureListener { e ->
                Log.e(TAG, "Failed removing geofence", e)
            }
        }
    }
}