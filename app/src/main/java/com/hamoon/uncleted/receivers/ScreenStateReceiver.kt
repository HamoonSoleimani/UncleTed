package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
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
                Log.d(TAG, "Screen-off event detected. Triggering memory hardening pass...")
                if (SecurityPreferences.isZramScrubbingEnabled(context)) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            MemoryHardeningEngine.executeVolatileScrub(context)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed executing memory hardening on screen-off", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present event detected (Device unlocked).")
            }
        }
    }
}