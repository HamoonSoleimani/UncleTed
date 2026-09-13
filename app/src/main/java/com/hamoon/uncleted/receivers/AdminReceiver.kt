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

class AdminReceiver : DeviceAdminReceiver() {

    private val TAG = "AdminReceiver"

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
        Log.d(TAG, "Device unlocked successfully. Resetting failed attempts.")
        SecurityPreferences.resetFailedAttempts(context)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordSucceeded(context, intent, user)
        Log.d(TAG, "Device unlocked successfully. Resetting failed attempts.")
        SecurityPreferences.resetFailedAttempts(context)
    }

    private fun handleFailedAttempt(context: Context) {
        SecurityPreferences.incrementFailedAttempts(context)
        val attempts = SecurityPreferences.getFailedAttempts(context)
        val isSelfieEnabled = SecurityPreferences.isIntruderSelfieEnabled(context)

        Log.w(TAG, "Native lockscreen PIN failed! Attempt count: $attempts (Selfie enabled: $isSelfieEnabled)")
        EventLogger.log(context, "Failed lockscreen PIN attempt #$attempts")

        if (isSelfieEnabled && attempts >= 3) {
            Log.e(TAG, "Triggering INTRUDER_SELFIE after $attempts failed attempts.")
            PanicActionService.trigger(
                context,
                "INTRUDER_SELFIE",
                PanicActionService.Severity.MEDIUM
            )
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        if (SecurityPreferences.isMaintenanceMode(context)) {
            Log.i(TAG, "Deactivation requested while in maintenance mode. Allowing action.")
            EventLogger.log(context, "Device Admin deactivation allowed via Maintenance Mode.")
            return "Maintenance mode is active. You may now proceed to deactivate the administrator."
        }

        Log.w(TAG, "HOSTILE: Deactivation of Device Admin requested! Intercepting action.")
        EventLogger.log(context, "ALERT: Hostile deactivation of Device Admin detected.")

        PanicActionService.trigger(context, "UNINSTALL_ATTEMPT", PanicActionService.Severity.HIGH)

        val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("REASON", "UNINSTALL_ATTEMPT")
        }
        context.startActivity(lockIntent)

        return context.getString(R.string.admin_disable_warning)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin has been enabled.")
        EventLogger.log(context, "Device Admin enabled successfully.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.e(TAG, "CRITICAL: Device Admin has been disabled.")
        EventLogger.log(context, "CRITICAL: Device Admin has been disabled.")
    }
}