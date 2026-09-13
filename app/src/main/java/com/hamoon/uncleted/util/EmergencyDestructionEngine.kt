package com.hamoon.uncleted.util

import android.content.Context
import android.os.PowerManager
import android.os.Process
import android.os.RecoverySystem
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Method

/**
 * Low-level hardware destruction and cryptographic eviction engine.
 * Combines OS platform privileges (MASTER_CLEAR) with kernel-level block zeroing.
 */
object EmergencyDestructionEngine {

    private const val TAG = "DestructionEngine"

    /**
     * Executes the most aggressive destruction protocol possible
     * based on the active runtime privileges (Root UID 0 vs Priv-App UID 1000).
     */
    suspend fun executeDestructionSequence(context: Context, reason: String) = withContext(Dispatchers.IO) {
        Log.e(TAG, "!!! INITIATING EMERGENCY DESTRUCTION SEQUENCE: $reason !!!")

        // 1. Cut all cellular and radio communications immediately to prevent tracking/remote kill abortion
        killCommunications(context)

        // 2. Cryptographic Eviction & Header Destruction (Sub-second execution)
        if (RootChecker.isDeviceRooted()) {
            evictAndZeroEncryptionKeys()
        }

        // 3. Platform RecoverySystem Wipe (Uses MASTER_CLEAR privilege)
        val platformWipeSuccess = triggerPlatformRecoveryWipe(context, reason)

        // 4. Fallback if platform wipe fails: Direct Kernel / Hardware Reset
        if (!platformWipeSuccess) {
            executeKernelRebootFallback()
        }
    }

    /**
     * Instantly cuts network traffic via iptables to prevent forensic capture over Wi-Fi/LTE.
     */
    private suspend fun killCommunications(context: Context) {
        try {
            if (RootChecker.isDeviceRooted()) {
                val dropCommands = listOf(
                    "iptables -F",
                    "iptables -X",
                    "iptables -t nat -F",
                    "iptables -t nat -X",
                    "iptables -t mangle -F",
                    "iptables -t mangle -X",
                    "iptables -P INPUT DROP",
                    "iptables -P FORWARD DROP",
                    "iptables -P OUTPUT DROP",
                    "ip6tables -P INPUT DROP",
                    "ip6tables -P FORWARD DROP",
                    "ip6tables -P OUTPUT DROP"
                )
                RootExecutor.runMultiple(dropCommands)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed cutting comms: ${t.message}")
        }
    }

    /**
     * Destroys File-Based Encryption (FBE) keys and metadata headers.
     * Rendering data on disk mathematically irrecoverable in under 500ms.
     */
    private suspend fun evictAndZeroEncryptionKeys() {
        Log.e(TAG, "Zeroing FBE key directories and partition superblocks...")

        val destructionCommands = listOf(
            // A. Evict Linux Kernel Keyrings
            "keyctl clear @u",
            "keyctl clear @s",

            // B. Zero FBE / Vold user key storage
            "rm -rf /data/misc/vold/user_keys/*",
            "rm -rf /metadata/vold/user_keys/*",
            "rm -rf /data/system/users/0/*.key",

            // C. Overwrite Vold metadata encryption keys with zeros
            "find /metadata/vold/ -type f -exec dd if=/dev/zero of={} bs=4096 count=10 conv=fsync \\;",

            // D. Zero out partition headers (Superblock & LUKS/FBE headers)
            "dd if=/dev/zero of=/dev/block/bootdevice/by-name/metadata bs=1048576 count=10 conv=fsync",
            "dd if=/dev/zero of=/dev/block/by-name/metadata bs=1048576 count=10 conv=fsync",
            "dd if=/dev/zero of=/dev/block/bootdevice/by-name/userdata bs=1048576 count=50 conv=fsync",
            "dd if=/dev/zero of=/dev/block/by-name/userdata bs=1048576 count=50 conv=fsync"
        )

        RootExecutor.runMultiple(destructionCommands)
    }

    /**
     * Invokes RecoverySystem.rebootWipeUserData with MASTER_CLEAR authority.
     * Uses reflection to handle differing parameter counts across Android 10-15.
     */
    fun triggerPlatformRecoveryWipe(context: Context, reason: String): Boolean {
        return try {
            Log.i(TAG, "Invoking RecoverySystem.rebootWipeUserData via platform authority...")

            val recoverySystemClass = RecoverySystem::class.java
            val methods = recoverySystemClass.declaredMethods
            var wipeMethod: Method? = null

            for (m in methods) {
                if (m.name == "rebootWipeUserData") {
                    wipeMethod = m
                    break
                }
            }

            if (wipeMethod != null) {
                wipeMethod.isAccessible = true
                val paramTypes = wipeMethod.parameterTypes
                when (paramTypes.size) {
                    5 -> wipeMethod.invoke(null, context, false, reason, true, false)
                    4 -> wipeMethod.invoke(null, context, false, reason, true)
                    3 -> wipeMethod.invoke(null, context, reason, false)
                    else -> wipeMethod.invoke(null, context, false, reason)
                }
                true
            } else {
                Log.w(TAG, "rebootWipeUserData method not found on RecoverySystem.")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Platform wipe failed: ${e.message}", e)
            false
        }
    }

    /**
     * Fallback reboot via raw shell/root to force device into recovery for format.
     */
    private suspend fun executeKernelRebootFallback() {
        Log.e(TAG, "Executing low-level kernel reboot fallback...")
        if (RootChecker.isDeviceRooted()) {
            RootExecutor.run("reboot recovery")
            RootExecutor.run("reboot -f")
            RootExecutor.run("echo c > /proc/sysrq-trigger") // Kernel panic trigger
        } else {
            try {
                Runtime.getRuntime().exec(arrayOf("reboot", "recovery"))
            } catch (_: Exception) {}
        }
    }
}