package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.sentinels.UsbTrapdoorController
import com.hamoon.uncleted.util.MemoryHardeningEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen-off event detected. Triggering zero-latency USB severing and memory purge...")

                // Zero-latency USB PHY cut upon screen-off
                if (SecurityPreferences.isUsbTripwireEnabled(context)) {
                    UsbTrapdoorController.armTrapdoor(context)
                }

                if (SecurityPreferences.isZramScrubbingEnabled(context)) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            MemoryHardeningEngine.executeVolatileScrub(context)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed executing memory hardening on screen-off", e)
                        } finally {
                            try {
                                pendingResult.finish()
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present event detected (Device unlocked). Restoring authorized USB data bus.")
                if (SecurityPreferences.isUsbTripwireEnabled(context)) {
                    UsbTrapdoorController.disarmTrapdoor(context)
                }
            }
        }
    }
}