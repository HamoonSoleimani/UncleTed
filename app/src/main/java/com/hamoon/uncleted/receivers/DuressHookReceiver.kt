package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.EventLogger

class DuressHookReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UncleTed-LockHook"
        const val ACTION_DURESS_TRIGGERED = "com.hamoon.uncleted.ACTION_DURESS_TRIGGERED"
        const val ACTION_LOCKSCREEN_FAILED_ATTEMPT = "com.hamoon.uncleted.ACTION_LOCKSCREEN_FAILED_ATTEMPT"
        const val ACTION_LOCKSCREEN_SUCCESS = "com.hamoon.uncleted.ACTION_LOCKSCREEN_SUCCESS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DURESS_TRIGGERED -> {
                Log.w(TAG, "OS-Level Duress Broadcast Received! Triggering PanicActionService.")
                EventLogger.log(context, "OS Hook: Native lockscreen Duress PIN triggered.")
                PanicActionService.trigger(
                    context,
                    "DURESS_PIN",
                    PanicActionService.Severity.HIGH
                )
            }
            ACTION_LOCKSCREEN_FAILED_ATTEMPT -> {
                SecurityPreferences.incrementFailedAttempts(context)
                val attempts = SecurityPreferences.getFailedAttempts(context)
                val isSelfieEnabled = SecurityPreferences.isIntruderSelfieEnabled(context)

                Log.w(TAG, "Lockscreen PIN failed! Count: $attempts (Selfie enabled: $isSelfieEnabled)")
                EventLogger.log(context, "Lockscreen authentication failed. Attempt #$attempts")

                if (isSelfieEnabled && attempts >= 3) {
                    Log.e(TAG, "Threshold reached ($attempts attempts >= 3). Triggering INTRUDER_SELFIE.")
                    PanicActionService.trigger(
                        context,
                        "INTRUDER_SELFIE",
                        PanicActionService.Severity.MEDIUM
                    )
                }
            }
            ACTION_LOCKSCREEN_SUCCESS -> {
                Log.i(TAG, "Device unlocked successfully. Resetting failed PIN attempts.")
                SecurityPreferences.resetFailedAttempts(context)
            }
        }
    }
}