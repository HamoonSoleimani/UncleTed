package com.hamoon.uncleted.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.hamoon.uncleted.receivers.AdminReceiver
import kotlinx.coroutines.runBlocking

object DeviceAdminHelper {
    private const val TAG = "DeviceAdminHelper"

    fun wipeDeviceImmediately(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, AdminReceiver::class.java)

        Log.w(TAG, "Initiating sequential wipe sequence...")

        // 1. BLOCKING EXECUTION: Run low-level destruction routines to completion BEFORE signaling OS reboot
        try {
            runBlocking {
                EmergencyDestructionEngine.executeDestructionSequence(context, "DeviceAdmin_Emergency_Wipe")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Pre-wipe destruction sequence error: ${t.message}", t)
        }

        // 2. DEVICE ADMIN STRATEGY (If Device Admin is active)
        if (dpm.isAdminActive(adminComponent)) {
            try {
                Log.i(TAG, "Device Admin is active. Calling dpm.wipeData(0)...")
                dpm.wipeData(0)
                return
            } catch (e: Exception) {
                Log.e(TAG, "DPM standard wipe failed: ${e.message}")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    Log.i(TAG, "Attempting DPM silent wipe...")
                    dpm.wipeData(DevicePolicyManager.WIPE_SILENTLY)
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "DPM silent wipe failed: ${e.message}")
                }
            }
        } else {
            Log.w(TAG, "Device Admin is NOT active. Skipping DPM wipeData calls.")
        }

        // 3. PLATFORM RECOVERY FALLBACK: If DPM failed or wasn't active, use platform authority
        val platformSuccess = EmergencyDestructionEngine.triggerPlatformRecoveryWipe(context, "Fallback_Wipe")
        if (platformSuccess) return

        // 4. SCORCHED EARTH FAILSAFE: If wipe failed entirely, lock screen and reboot immediately
        try {
            if (dpm.isAdminActive(adminComponent)) {
                dpm.lockNow()
                dpm.reboot(adminComponent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lock/reboot failsafe failed: ${e.message}")
        }
    }
}