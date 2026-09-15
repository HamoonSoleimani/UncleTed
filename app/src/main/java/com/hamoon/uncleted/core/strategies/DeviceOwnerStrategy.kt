package com.hamoon.uncleted.core.strategies

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.hamoon.uncleted.core.DefenseStrategy

class DeviceOwnerStrategy(
    private val context: Context,
    private val dpm: DevicePolicyManager,
    private val adminComponent: ComponentName
) : DefenseStrategy {

    companion object {
        private const val TAG = "DeviceOwnerStrategy"
    }

    override val profileName: String = "DEVICE_OWNER_AVB_LOCKED"
    override val isHardwareSecured: Boolean = true

    override suspend fun executeWipe(reason: String) {
        Log.e(TAG, "Executing hardware cryptographic erasure via Secure Element (Reason: $reason)")
        try {
            // Instantly revokes master key blobs in Weaver/KeyMint/Titan M2. Sub-second execution.
            dpm.wipeData(
                DevicePolicyManager.WIPE_EXTERNAL_STORAGE or DevicePolicyManager.WIPE_SILENTLY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Standard silent wipe failed, falling back to legacy wipeData flag", e)
            dpm.wipeData(0)
        }
    }

    override suspend fun setUsbDataPortEnabled(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // Commands the hardware USB Type-C HAL to physically disconnect D+/D- signaling lines
                dpm.setUsbDataSignalingEnabled(enabled)
                Log.i(TAG, "Hardware USB data signaling state set to: $enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle USB data signaling via HAL", e)
            }
        } else {
            Log.w(TAG, "setUsbDataSignalingEnabled requires Android 12+ (API 31+)")
        }
    }

    override suspend fun configureBruteForceThreshold(maxFailedAttempts: Int) {
        try {
            // Direct delegation to Gatekeeper and Weaver hardware. The kernel triggers wipe autonomously.
            dpm.setMaximumFailedPasswordsForWipe(adminComponent, maxFailedAttempts)
            Log.i(TAG, "Hardware Gatekeeper max failed attempts configured to: $maxFailedAttempts")
        } catch (e: Exception) {
            Log.e(TAG, "Failed configuring hardware brute-force threshold", e)
        }
    }

    override suspend fun evictMemoryKeysAndLock() {
        Log.w(TAG, "Forcing instant Keyguard lock to drop user session")
        try {
            dpm.lockNow()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lock screen via DevicePolicyManager", e)
        }
    }

    override suspend fun disableBiometrics(disable: Boolean) {
        val flags = if (disable) {
            DevicePolicyManager.KEYGUARD_DISABLE_BIOMETRICS or
                    DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT or
                    DevicePolicyManager.KEYGUARD_DISABLE_FACE
        } else {
            0
        }
        try {
            dpm.setKeyguardDisabledFeatures(adminComponent, flags)
            Log.i(TAG, "Keyguard biometric disable state: $disable")
        } catch (e: Exception) {
            Log.e(TAG, "Failed modifying Keyguard disabled features", e)
        }
    }
}