package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.honeypot.HoneypotLauncherActivity
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.DecoyUserManager
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.GodMode
import com.hamoon.uncleted.util.PermissionUtils
import com.hamoon.uncleted.util.RootChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DuressHookReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DuressHookReceiver"
        const val ACTION_DURESS_TRIGGERED = "com.hamoon.uncleted.ACTION_DURESS_TRIGGERED"
        const val ACTION_HONEYPOT_TRIGGERED = "com.hamoon.uncleted.ACTION_HONEYPOT_TRIGGERED"
        const val ACTION_LOCKSCREEN_FAILED_ATTEMPT = "com.hamoon.uncleted.ACTION_LOCKSCREEN_FAILED_ATTEMPT"
        const val ACTION_LOCKSCREEN_SUCCESS = "com.hamoon.uncleted.ACTION_LOCKSCREEN_SUCCESS"

        private const val DEDUPLICATION_WINDOW_MS = 2500L
        @Volatile
        private var lastHandledFailureTime = 0L
        @Volatile
        private var lastHoneypotTriggerTime = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val isPrimaryUser = (Process.myUid() / 100000) == 0
        if (!isPrimaryUser) {
            Log.d(TAG, "Running under secondary user space (UID: ${Process.myUid()}). Skipping duress receiver actions.")
            return
        }

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
                val now = System.currentTimeMillis()
                if (now - lastHoneypotTriggerTime < DEDUPLICATION_WINDOW_MS) {
                    Log.d(TAG, "Suppressed duplicate Honeypot trigger within debounce window.")
                    return
                }
                lastHoneypotTriggerTime = now

                Log.w(TAG, "Honeypot PIN signal received from hook. Transitioning to decoy space...")
                EventLogger.log(context, "OS Hook: Honeypot PIN entered. Active session transitioned to Decoy Space.")

                val targetUserId = intent.getIntExtra("DECOY_USER_ID", -1)
                val alreadySwitched = intent.getBooleanExtra("ALREADY_SWITCHED", false)

                // Complete the broadcast immediately to avoid holding system_server broadcast queues and throwing ANRs
                val pendingResult = goAsync()
                pendingResult.finish()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (alreadySwitched) {
                            Log.i(TAG, "User switch was already executed in-process by system_server hook. Deferring cache eviction...")
                            delay(4000L)
                            DecoyUserManager.evictPrimaryUserCeKeys(context)
                        } else {
                            val validId = if (targetUserId > 0) targetUserId else DecoyUserManager.getValidDecoyUserId(context)
                            if (validId > 0) {
                                DecoyUserManager.switchToDecoyWithCeEviction(context, validId)
                            } else {
                                val honeyIntent = Intent(context, HoneypotLauncherActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                if (RootChecker.isDeviceRooted()) {
                                    GodMode.startActivityInBackground(context, honeyIntent)
                                } else {
                                    context.startActivity(honeyIntent)
                                }
                                delay(3000L)
                                DecoyUserManager.evictPrimaryUserCeKeys(context)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed executing post-honeypot tasks in receiver", e)
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
                val maxAllowed = SecurityPreferences.getMaxFailedAttemptsForWipe(context)

                Log.w(TAG, "Hook recorded authentication failure. Count: $attempts")
                EventLogger.log(context, "Lockscreen authentication failed. Attempt #$attempts")

                if (maxAllowed in 1..attempts) {
                    Log.e(TAG, "Hook: Failed attempts ($attempts) reached wipe limit ($maxAllowed)! Wiping device.")
                    EventLogger.log(context, "CRITICAL: Max failed attempts reached ($attempts/$maxAllowed). Triggering wipe.")

                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val strategy = DefenseCoordinator.resolveStrategy(context)
                            strategy.executeWipe("MAX_FAILED_PASSWORDS_EXCEEDED")
                            PanicActionService.trigger(context, "REMOTE_WIPE", PanicActionService.Severity.CRITICAL)
                        } finally {
                            try {
                                pendingResult.finish()
                            } catch (_: Exception) {}
                        }
                    }
                    return
                }

                val isSelfieEnabled = SecurityPreferences.isIntruderSelfieEnabled(context)
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