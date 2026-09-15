package com.hamoon.uncleted.core.strategies

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.core.DefenseStrategy
import com.hamoon.uncleted.util.EmergencyDestructionEngine
import com.hamoon.uncleted.util.RootExecutor

class RootPrivilegedStrategy(
    private val context: Context
) : DefenseStrategy {

    companion object {
        private const val TAG = "RootPrivilegedStrategy"
    }

    override val profileName: String = "ROOT_LSPOSED_UNLOCKED"
    override val isHardwareSecured: Boolean = false

    override suspend fun executeWipe(reason: String) {
        Log.e(TAG, "Executing root-level cryptographic key eviction and reboot (Reason: $reason)")

        // 1. Flush and drop all IP tables to isolate device network interfaces
        EmergencyDestructionEngine.killCommunications()

        // 2. Erase Vold user keys from /data/misc/vold/ and zero metadata headers
        EmergencyDestructionEngine.evictAndZeroEncryptionKeys()

        // 3. Force instant low-level hardware reboot into recovery
        val rebootResult = RootExecutor.run("reboot recovery")
        if (!rebootResult.isSuccess) {
            RootExecutor.run("/system/bin/reboot recovery")
            RootExecutor.run("echo c > /proc/sysrq-trigger")
        }
    }

    override suspend fun setUsbDataPortEnabled(enabled: Boolean) {
        Log.i(TAG, "Manipulating USB Gadget controller via elevated shell: enabled=$enabled")
        if (!enabled) {
            val script = listOf(
                "setprop sys.usb.config none",
                "setprop sys.usb.state none",
                "echo '' > /config/usb_gadget/g1/UDC 2>/dev/null || true"
            )
            RootExecutor.runMultiple(script)
        } else {
            RootExecutor.run("setprop sys.usb.config mtp,adb")
        }
    }

    override suspend fun configureBruteForceThreshold(maxFailedAttempts: Int) {
        Log.i(TAG, "Brute force threshold in root mode enforced by system_server LockscreenHook: $maxFailedAttempts")
    }

    override suspend fun evictMemoryKeysAndLock() {
        Log.w(TAG, "Locking device and dropping user session via root keyevent")
        RootExecutor.run("input keyevent 26")
    }

    override suspend fun disableBiometrics(disable: Boolean) {
        Log.i(TAG, "Biometric toggling via Root/LSPosed state modifier: $disable")
        val value = if (disable) "1" else "0"
        RootExecutor.run("settings put secure biometric_keyguard_disabled $value")
    }
}