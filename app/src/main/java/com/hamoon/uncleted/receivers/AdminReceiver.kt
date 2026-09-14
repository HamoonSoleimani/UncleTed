package com.hamoon.uncleted.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import com.hamoon.uncleted.LockScreenActivity
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.GodMode
import com.hamoon.uncleted.util.RootChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AdminReceiver"
        private const val ATTEMPT_DEDUPLICATION_WINDOW_MS = 1500L

        @Volatile
        private var lastHandledAttemptTime = 0L
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        handleFailedAttempt(context)
    }

    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordFailed(context, intent, user)
        handleFailedAttempt(context)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Lockscreen authentication succeeded via DeviceAdmin. Resetting failed attempts.")
        SecurityPreferences.resetFailedAttempts(context)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordSucceeded(context, intent, user)
        Log.d(TAG, "Lockscreen authentication succeeded via DeviceAdmin. Resetting failed attempts.")
        SecurityPreferences.resetFailedAttempts(context)
    }

    private fun handleFailedAttempt(context: Context) {
        val now = System.currentTimeMillis()
        synchronized(AdminReceiver::class.java) {
            if (now - lastHandledAttemptTime < ATTEMPT_DEDUPLICATION_WINDOW_MS) {
                Log.d(TAG, "Ignoring duplicate password failure event within cooldown window.")
                return
            }
            lastHandledAttemptTime = now
        }

        SecurityPreferences.incrementFailedAttempts(context)
        val attempts = SecurityPreferences.getFailedAttempts(context)
        val isSelfieEnabled = SecurityPreferences.isIntruderSelfieEnabled(context)

        Log.w(TAG, "Lockscreen authentication failed. Attempt count: $attempts (Selfie enabled: $isSelfieEnabled)")
        EventLogger.log(context, "Failed lockscreen authentication attempt #$attempts")

        if (isSelfieEnabled && attempts >= 3) {
            Log.e(TAG, "Threshold reached ($attempts attempts). Triggering INTRUDER_SELFIE.")
            PanicActionService.trigger(
                context,
                "INTRUDER_SELFIE",
                PanicActionService.Severity.MEDIUM
            )
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        if (SecurityPreferences.isMaintenanceMode(context)) {
            Log.i(TAG, "Admin deactivation requested in maintenance mode.")
            EventLogger.log(context, "Device Admin deactivation authorized via Maintenance Mode.")
            return "Maintenance mode is active. Administrator deactivation permitted."
        }

        Log.w(TAG, "Unauthorized attempt to deactivate Device Admin. Triggering alert.")
        EventLogger.log(context, "ALERT: Hostile Device Admin deactivation detected.")

        PanicActionService.trigger(context, "UNINSTALL_ATTEMPT", PanicActionService.Severity.HIGH)

        val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("REASON", "UNINSTALL_ATTEMPT")
        }

        // Bypass Android 10+ Background Activity Launch (BAL) restrictions cleanly
        CoroutineScope(Dispatchers.IO).launch {
            if (RootChecker.isDeviceRooted()) {
                GodMode.startActivityInBackground(context, lockIntent)
            } else {
                try {
                    context.startActivity(lockIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Standard background activity start dropped by OS BAL restrictions", e)
                }
            }
        }

        return context.getString(R.string.admin_disable_warning)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled.")
        EventLogger.log(context, "Device Admin enabled successfully.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.e(TAG, "CRITICAL: Device Admin has been disabled.")
        EventLogger.log(context, "CRITICAL: Device Admin disabled.")
    }
}