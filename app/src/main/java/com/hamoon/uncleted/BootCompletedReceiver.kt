package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.MonitoringService
import com.hamoon.uncleted.services.UsbTripwireService
import com.hamoon.uncleted.services.ZoneWipeService
import com.hamoon.uncleted.util.TripwireManager
import com.hamoon.uncleted.util.WatchdogManager

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isUnlocked = SecurityPreferences.isUserUnlocked(context)

        Log.d(TAG, "Device boot event received: $action (User unlocked: $isUnlocked)")

        // 1. Direct Boot / BFU (Before First Unlock) Phase
        // Always safe to execute because SecurityPreferences operates on Device-Protected (DE) storage
        SecurityPreferences.syncHookCredentials(context)

        if (SecurityPreferences.isUsbTripwireEnabled(context)) {
            val usbIntent = Intent(context, UsbTripwireService::class.java)
            try {
                ContextCompat.startForegroundService(context, usbIntent)
                Log.i(TAG, "Started UsbTripwireService on boot.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start UsbTripwireService on boot", e)
            }
        }

        if (SecurityPreferences.isGeofenceSuicideEnabled(context)) {
            val zoneIntent = Intent(context, ZoneWipeService::class.java)
            try {
                ContextCompat.startForegroundService(context, zoneIntent)
                Log.i(TAG, "Started ZoneWipeService on boot.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ZoneWipeService on boot", e)
            }
        }

        // 2. Credential-Encrypted (CE) Phase
        // WorkManager and user services initialize only once the user credentials decrypt CE storage
        if (isUnlocked) {
            if (SecurityPreferences.isProtectionEnabled(context)) {
                val serviceIntent = Intent(context, MonitoringService::class.java)
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                    Log.i(TAG, "Started MonitoringService on post-unlock boot.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed starting MonitoringService on post-unlock boot", e)
                }
            }

            if (SecurityPreferences.isWatchdogModeEnabled(context)) {
                WatchdogManager.scheduleOrCancelWatchdog(context)
                Log.i(TAG, "Rescheduled WatchdogWorker on post-unlock boot.")
            }

            if (SecurityPreferences.isTripwireEnabled(context)) {
                TripwireManager.scheduleFromLastCheckIn(context)
                Log.i(TAG, "Rescheduled TripwireWorker on post-unlock boot.")
            }
        } else {
            Log.i(TAG, "Device remains Before First Unlock (BFU). Skipping CE-dependent tasks.")
        }
    }
}