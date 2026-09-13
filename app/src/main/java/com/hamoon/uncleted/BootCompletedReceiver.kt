package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.MonitoringService
import com.hamoon.uncleted.util.TripwireManager
import com.hamoon.uncleted.util.WatchdogManager

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d("BootCompletedReceiver", "Device booted ($action). Synchronizing security services.")

            // Refresh credential hook bridge on boot
            SecurityPreferences.syncHookCredentials(context)

            if (SecurityPreferences.isProtectionEnabled(context)) {
                val serviceIntent = Intent(context, MonitoringService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
                Log.i("BootCompletedReceiver", "Started MonitoringService on boot.")
            }

            if (SecurityPreferences.isWatchdogModeEnabled(context)) {
                WatchdogManager.scheduleOrCancelWatchdog(context)
                Log.i("BootCompletedReceiver", "Rescheduled WatchdogWorker on boot.")
            }

            if (SecurityPreferences.isTripwireEnabled(context)) {
                TripwireManager.scheduleFromLastCheckIn(context)
                Log.i("BootCompletedReceiver", "Rescheduled TripwireWorker on boot.")
            }
        }
    }
}