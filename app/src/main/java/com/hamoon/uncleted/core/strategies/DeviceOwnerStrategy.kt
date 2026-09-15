package com.hamoon.uncleted.core.strategies

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.hamoon.uncleted.core.DefenseStrategy
import com.hamoon.uncleted.crypto.StrongBoxSecurityManager
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.RadioIsolationManager

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
        EventLogger.log(context, "CRITICAL: Hardware-backed cryptographic wipe triggered: $reason")

        // 1. Immediately destroy discrete StrongBox Master Suicide Key in silicon
        StrongBoxSecurityManager.executeMasterKeySuicide(context)

        // 2. Sever all radio communications
        isolateRadiosAndNetwork()

        // 3. Command Titan M2 / Weaver / KeyMint to revoke all File-Based Encryption keys
        try {
            dpm.wipeData(
                DevicePolicyManager.WIPE_EXTERNAL_STORAGE or DevicePolicyManager.WIPE_SILENTLY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Standard silent wipe failed, falling back to legacy wipeData flag", e)
            dpm.wipeData(0)
        }
    }

    override suspend fun setUsbDataPortEnabled(enabled: Boolean) {
        Log.i(TAG, "Configuring hardware USB data signaling: enabled=$enabled")
        EventLogger.log(context, "HARDWARE: USB data signaling toggled: enabled=$enabled")

        // Method 1: Android 12+ (API 31+) USB HAL v1.3+ physical line disconnect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                dpm.setUsbDataSignalingEnabled(enabled)
                Log.i(TAG, "USB Type-C HAL data signaling state set to: $enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed calling setUsbDataSignalingEnabled via USB HAL", e)
            }
        } else {
            Log.w(TAG, "Physical USB HAL disconnect requires Android 12+ (API 31+). Applying user restrictions fallback.")
        }

        // Method 2: Android Enterprise User Restrictions fallback and defense-in-depth
        try {
            if (!enabled) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
                }
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed modifying USB policy restrictions", e)
        }
    }

    override suspend fun configureBruteForceThreshold(maxFailedAttempts: Int) {
        try {
            // Direct hardware rate-limiting delegation to Titan M / Weaver chip
            dpm.setMaximumFailedPasswordsForWipe(adminComponent, maxFailedAttempts)
            Log.i(TAG, "Hardware Gatekeeper/Weaver max failed attempts configured to: $maxFailedAttempts")
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

    override suspend fun isolateRadiosAndNetwork() {
        Log.e(TAG, "Applying Device Owner radio isolation policies...")
        try {
            dpm.setGlobalSetting(adminComponent, Settings.Global.AIRPLANE_MODE_ON, "1")
        } catch (e: Exception) {
            Log.w(TAG, "Failed enforcing global airplane mode via DPM: ${e.message}")
        }
        RadioIsolationManager.isolateAllCommunications(context)
    }
}