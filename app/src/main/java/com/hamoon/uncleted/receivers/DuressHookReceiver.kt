package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.DecoyUserManager
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DuressHookReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DuressHookReceiver"
        const val ACTION_DURESS_TRIGGERED = "com.hamoon.uncleted.ACTION_DURESS_TRIGGERED"
        const val ACTION_HONEYPOT_TRIGGERED = "com.hamoon.uncleted.ACTION_HONEYPOT_TRIGGERED"
        const val ACTION_LOCKSCREEN_FAILED_ATTEMPT = "com.hamoon.uncleted.ACTION_LOCKSCREEN_FAILED_ATTEMPT"
        const val ACTION_LOCKSCREEN_SUCCESS = "com.hamoon.uncleted.ACTION_LOCKSCREEN_SUCCESS"

        private const val DEDUPLICATION_WINDOW_MS = 1500L
        @Volatile
        private var lastHandledFailureTime = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DURESS_TRIGGERED -> {
                Log.w(TAG, "Duress PIN detected by native hook. Triggering covert emergency protocol.")
                EventLogger.log(context, "OS Hook: Duress PIN intercepted on Keyguard.")
                PanicActionService.trigger(
                    context,
                    "DURESS_PIN_LOCKSCREEN",
                    PanicActionService.Severity.HIGH
                )
            }
            ACTION_HONEYPOT_TRIGGERED -> {
                Log.w(TAG, "Honeypot PIN detected by native hook. Engaging surveillance and volatile RAM eviction.")
                EventLogger.log(context, "OS Hook: Honeypot PIN entered. Migrating session and evicting User 0 keys.")

                PanicActionService.trigger(
                    context,
                    "HONEYPOT_PIN_LOCKSCREEN",
                    PanicActionService.Severity.HIGH
                )

                // Asynchronous watchdog fallback: ensure Vold eviction completes even if system_server reflection was delayed
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        DecoyUserManager.evictPrimaryUserCeKeys(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_LOCKSCREEN_FAILED_ATTEMPT -> {
                if (PermissionUtils.isDeviceAdminActive(context)) {
                    Log.d(TAG, "Device Admin active; delegating failure tracking to AdminReceiver.")
                    return
                }

                val now = System.currentTimeMillis()
                synchronized(DuressHookReceiver::class.java) {
                    if (now - lastHandledFailureTime < DEDUPLICATION_WINDOW_MS) {
                        return
                    }
                    lastHandledFailureTime = now
                }

                SecurityPreferences.incrementFailedAttempts(context)
                val attempts = SecurityPreferences.getFailedAttempts(context)
                val isSelfieEnabled = SecurityPreferences.isIntruderSelfieEnabled(context)

                Log.w(TAG, "Hook recorded authentication failure. Count: $attempts (Selfie enabled: $isSelfieEnabled)")
                EventLogger.log(context, "Lockscreen authentication failed. Attempt #$attempts")

                if (isSelfieEnabled && attempts >= 3) {
                    Log.e(TAG, "Threshold reached ($attempts attempts). Triggering INTRUDER_SELFIE.")
                    PanicActionService.trigger(
                        context,
                        "INTRUDER_SELFIE",
                        PanicActionService.Severity.MEDIUM
                    )
                }
            }
            ACTION_LOCKSCREEN_SUCCESS -> {
                Log.i(TAG, "Lockscreen unlocked successfully. Resetting failed attempt count.")
                SecurityPreferences.resetFailedAttempts(context)
            }
        }
    }
}