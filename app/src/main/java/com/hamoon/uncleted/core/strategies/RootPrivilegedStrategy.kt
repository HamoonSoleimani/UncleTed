package com.hamoon.uncleted.core.strategies

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.core.DefenseStrategy
import com.hamoon.uncleted.crypto.StrongBoxSecurityManager
import com.hamoon.uncleted.util.EmergencyDestructionEngine
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.RadioIsolationManager
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
        EventLogger.log(context, "CRITICAL: Root-level destruction invoked: $reason")

        // 1. Destroy discrete StrongBox key if available
        StrongBoxSecurityManager.executeMasterKeySuicide(context)

        // 2. Flush and drop all IP tables to isolate device network interfaces
        isolateRadiosAndNetwork()

        // 3. Erase Vold user keys from /data/misc/vold/ and zero metadata headers
        EmergencyDestructionEngine.evictAndZeroEncryptionKeys()

        // 4. Force instant low-level hardware reboot into recovery
        val rebootResult = RootExecutor.run("reboot recovery")
        if (!rebootResult.isSuccess) {
            RootExecutor.run("/system/bin/reboot recovery")
            RootExecutor.run("echo c > /proc/sysrq-trigger")
        }
    }

    override suspend fun setUsbDataPortEnabled(enabled: Boolean) {
        Log.i(TAG, "Manipulating USB Gadget controller via elevated shell: enabled=$enabled")
        EventLogger.log(context, "HARDWARE: Root USB gadget controller state modified: enabled=$enabled")

        if (!enabled) {
            val script = listOf(
                "setprop sys.usb.config none",
                "setprop sys.usb.state none",
                "for udc in /sys/class/udc/*; do echo '' > \"\$udc/state\" 2>/dev/null || true; done",
                "echo '' > /config/usb_gadget/g1/UDC 2>/dev/null || true",
                "echo '' > /sys/class/android_usb/android0/enable 2>/dev/null || true"
            )
            RootExecutor.runMultiple(script, logErrors = false)
        } else {
            val script = listOf(
                "setprop sys.usb.config mtp,adb",
                "setprop sys.usb.state mtp,adb"
            )
            RootExecutor.runMultiple(script, logErrors = false)
        }
    }

    override suspend fun configureBruteForceThreshold(maxFailedAttempts: Int) {
        Log.i(TAG, "Brute force threshold in root mode enforced by system_server LockscreenHook: $maxFailedAttempts")
    }

    override suspend fun evictMemoryKeysAndLock() {
        Log.w(TAG, "Locking device and dropping user session via root keyevent and Vold lock")
        RootExecutor.run("vdc cryptfs lockuser 0")
        RootExecutor.run("sm lock-user-key 0")
        RootExecutor.run("sync")
        RootExecutor.run("echo 3 > /proc/sys/vm/drop_caches")
        RootExecutor.run("input keyevent 26")
    }

    override suspend fun disableBiometrics(disable: Boolean) {
        Log.i(TAG, "Biometric toggling via Root/LSPosed state modifier: $disable")
        val value = if (disable) "1" else "0"
        RootExecutor.run("settings put secure biometric_keyguard_disabled $value")
    }

    override suspend fun isolateRadiosAndNetwork() {
        Log.e(TAG, "Executing kernel iptables packet DROP and radio shutdown...")
        RadioIsolationManager.isolateAllCommunications(context)
    }

    override suspend fun cutBasebandRadioHardware() {
        Log.e(TAG, "Executing hardware-level RIL power cut via root...")
        EventLogger.log(context, "BASEBAND: Cutting modem RIL power bus via shell.")

        val rils = listOf(
            // Method 1: Telephony IPC service shutdown (turns off cellular modem power)
            "service call phone 83 i32 0 2>/dev/null || true",
            // Method 2: Radio interface layer daemon termination
            "stop ril-daemon 2>/dev/null || true",
            "stop vendor.ril-daemon 2>/dev/null || true",
            // Method 3: Cellular data and radio kill commands
            "svc data disable",
            "cmd connectivity airplane-mode enable",
            "settings put global airplane_mode_on 1"
        )
        RootExecutor.runMultiple(rils, logErrors = false)
    }
}