@file:Suppress("DEPRECATION")

package com.hamoon.uncleted.receivers

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import com.hamoon.uncleted.LockScreenActivity
import com.hamoon.uncleted.R
import com.hamoon.uncleted.core.DefenseCoordinator
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

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, AdminReceiver::class.java)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled. Initializing hardware baseline policies.")
        EventLogger.log(context, "Device Admin enabled successfully.")

        val dpm = getManager(context)
        val admin = getWho(context)

        CoroutineScope(Dispatchers.IO).launch {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                try {
                    dpm.setStorageEncryption(admin, true)
                    dpm.setMaximumFailedPasswordsForWipe(admin, 5)
                    dpm.setPasswordQuality(admin, DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX)
                    dpm.setPasswordMinimumLength(admin, 6)

                    Log.i(TAG, "Device Owner hardware zero-trust baseline enforced.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed configuring initial Device Owner policies", e)
                }
            }
        }
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        handlePasswordFailure(context)
    }

    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordFailed(context, intent, user)
        handlePasswordFailure(context)
    }

    private fun handlePasswordFailure(context: Context) {
        val now = System.currentTimeMillis()
        synchronized(AdminReceiver::class.java) {
            if (now - lastHandledAttemptTime < ATTEMPT_DEDUPLICATION_WINDOW_MS) {
                return
            }
            lastHandledAttemptTime = now
        }

        val dpm = getManager(context)
        val currentFailed = dpm.getCurrentFailedPasswordAttempts()
        Log.w(TAG, "Authentication failure detected. Hardware count: $currentFailed")
        EventLogger.log(context, "Hardware Keyguard authentication failed (Count: $currentFailed)")

        SecurityPreferences.incrementFailedAttempts(context)

        CoroutineScope(Dispatchers.IO).launch {
            val strategy = DefenseCoordinator.resolveStrategy(context)

            if (currentFailed >= 3) {
                Log.e(TAG, "Threshold >= 3 reached. Physically disabling USB port and locking biometrics.")
                strategy.setUsbDataPortEnabled(false)
                strategy.disableBiometrics(true)

                if (SecurityPreferences.isIntruderSelfieEnabled(context)) {
                    PanicActionService.trigger(
                        context,
                        "INTRUDER_SELFIE",
                        PanicActionService.Severity.MEDIUM
                    )
                }
            }
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        handlePasswordSuccess(context)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordSucceeded(context, intent, user)
        handlePasswordSuccess(context)
    }

    private fun handlePasswordSuccess(context: Context) {
        Log.d(TAG, "Lockscreen authentication succeeded. Resetting state.")
        SecurityPreferences.resetFailedAttempts(context)

        val deContext = context.createDeviceProtectedStorageContext()
        deContext.getSharedPreferences("deadman_state", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_authenticated_epoch", System.currentTimeMillis())
            .apply()

        CoroutineScope(Dispatchers.IO).launch {
            val strategy = DefenseCoordinator.resolveStrategy(context)
            strategy.disableBiometrics(false)
            strategy.setUsbDataPortEnabled(true)
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        if (SecurityPreferences.isMaintenanceMode(context)) {
            Log.i(TAG, "Admin deactivation authorized in maintenance mode.")
            return "Maintenance mode active. Deactivation permitted."
        }

        Log.w(TAG, "Hostile Device Admin deactivation detected. Triggering alert.")
        EventLogger.log(context, "ALERT: Unauthorized Device Admin deactivation attempt.")

        PanicActionService.trigger(context, "UNINSTALL_ATTEMPT", PanicActionService.Severity.HIGH)

        val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("REASON", "UNINSTALL_ATTEMPT")
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (RootChecker.isDeviceRooted()) {
                GodMode.startActivityInBackground(context, lockIntent)
            } else {
                try {
                    context.startActivity(lockIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed launching lockscreen from deactivation hook", e)
                }
            }
        }

        return context.getString(R.string.admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.e(TAG, "CRITICAL: Device Admin has been disabled.")
        EventLogger.log(context, "CRITICAL: Device Admin disabled.")
    }
}