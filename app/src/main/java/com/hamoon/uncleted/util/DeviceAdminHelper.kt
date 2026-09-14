package com.hamoon.uncleted.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.receivers.AdminReceiver
import kotlinx.coroutines.runBlocking

object DeviceAdminHelper {
    private const val TAG = "DeviceAdminHelper"

    /**
     * Executes the appropriate device wipe strategy.
     * Prevents premature block zeroing from corrupting standard DeviceAdmin or RecoverySystem invocations.
     */
    fun wipeDeviceImmediately(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, AdminReceiver::class.java)

        Log.w(TAG, "Initiating sequential wipe sequence...")

        val isRooted = runBlocking { RootChecker.isDeviceRooted() }
        val isSecureWipeConfigured = SecurityPreferences.isSecureWipeEnabled(context)

        // 1. If Rooted AND Secure Shredding is explicitly requested, execute the destructive block pipeline directly
        if (isRooted && isSecureWipeConfigured) {
            Log.i(TAG, "Root Secure Wipe configured. Executing destructive shred pipeline...")
            runBlocking {
                EmergencyDestructionEngine.executeDestructionSequence(context, "Emergency_Secure_Wipe")
            }
            return
        }

        // 2. Standard Safe Wipe: Attempt DevicePolicyManager wipeData first
        if (dpm.isAdminActive(adminComponent)) {
            try {
                Log.i(TAG, "Device Admin is active. Invoking dpm.wipeData(0)...")
                dpm.wipeData(0)
                return
            } catch (e: Exception) {
                Log.e(TAG, "DPM standard wipeData failed: ${e.message}")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    Log.i(TAG, "Attempting DPM WIPE_SILENTLY...")
                    dpm.wipeData(DevicePolicyManager.WIPE_SILENTLY)
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "DPM silent wipe failed: ${e.message}")
                }
            }
        } else {
            Log.w(TAG, "Device Admin is NOT active. Falling back to RecoverySystem platform wipe.")
        }

        // 3. Fallback: Invoke RecoverySystem via MASTER_CLEAR platform authority
        val platformSuccess = EmergencyDestructionEngine.triggerPlatformRecoveryWipe(context, "Fallback_Platform_Wipe")
        if (platformSuccess) return

        // 4. Low-Level Fallback: If platform methods failed, use Root destruction or hardware reboot
        if (isRooted) {
            Log.e(TAG, "Platform wipe routines failed. Falling back to low-level block zeroing...")
            runBlocking {
                EmergencyDestructionEngine.executeDestructionSequence(context, "Emergency_LowLevel_Fallback")
            }
        } else {
            // Lock screen as last failsafe
            try {
                if (dpm.isAdminActive(adminComponent)) {
                    dpm.lockNow()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failsafe lock failed: ${e.message}")
            }
        }
    }
}