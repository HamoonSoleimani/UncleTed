package com.hamoon.uncleted.util

import android.content.Context
import android.os.RecoverySystem
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Method

object EmergencyDestructionEngine {

    private const val TAG = "DestructionEngine"

    suspend fun executeDestructionSequence(context: Context, reason: String) = withContext(Dispatchers.IO) {
        Log.e(TAG, "!!! INITIATING EMERGENCY DESTRUCTION SEQUENCE: $reason !!!")

        // 1. Cut all network communications immediately via iptables DROP
        killCommunications()

        val isRooted = RootChecker.isDeviceRooted()

        if (isRooted && SecurityPreferences.isSecureWipeEnabled(context)) {
            // Low-level cryptographic key eviction and metadata destruction
            evictAndZeroEncryptionKeys()
            destroyPrimaryBlockHeaders()
            executeKernelRebootFallback()
        } else {
            // Platform Recovery wipe path
            val platformWipeSuccess = triggerPlatformRecoveryWipe(context, reason)
            if (!platformWipeSuccess) {
                if (isRooted) {
                    evictAndZeroEncryptionKeys()
                    executeKernelRebootFallback()
                } else {
                    executeKernelRebootFallback()
                }
            }
        }
    }

    suspend fun killCommunications() {
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
                    "ip6tables -F",
                    "ip6tables -X",
                    "ip6tables -P INPUT DROP",
                    "ip6tables -P FORWARD DROP",
                    "ip6tables -P OUTPUT DROP"
                )
                RootExecutor.runMultiple(dropCommands)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed isolating network interfaces: ${t.message}")
        }
    }

    /**
     * Evicts File-Based Encryption (FBE) cryptographic metadata headers.
     * Overwriting the metadata partition destroys key-encryption keys (KEKs),
     * rendering /data permanently unrecoverable without corrupting the active mounted block device.
     */
    suspend fun evictAndZeroEncryptionKeys() {
        Log.e(TAG, "Zeroing Vold cryptographic metadata and key slots...")

        val destructionCommands = listOf(
            // Overwrite Vold user keys on disk
            "rm -rf /data/misc/vold/user_keys/* 2>/dev/null || true",
            "rm -rf /metadata/vold/user_keys/* 2>/dev/null || true",
            "rm -rf /data/system/users/0/*.key 2>/dev/null || true"
        )
        RootExecutor.runMultiple(destructionCommands)

        // Zero out cryptographic metadata partition headers
        val metadataPath = findPartitionBlockPath("metadata")
        if (metadataPath != null) {
            RootExecutor.run("dd if=/dev/zero of=$metadataPath bs=1048576 count=16 conv=fsync")
        }

        RootExecutor.run("sync")
    }

    /**
     * Low-level GPT / Partition Table Destruction (Level 4 Nuclear Winter).
     */
    suspend fun destroyPrimaryBlockHeaders() {
        val primaryDisks = detectStorageDisks()
        for (disk in primaryDisks) {
            Log.e(TAG, "Overwriting primary block device headers on: $disk")
            // Zeroes the master partition table (MBR/GPT) and backup header
            RootExecutor.run("dd if=/dev/zero of=$disk bs=4096 count=2048 conv=fsync")
        }
        RootExecutor.run("sync")
    }

    fun detectStorageDisks(): List<String> {
        val foundDisks = mutableListOf<String>()

        val sysBlockResult = try {
            File("/sys/block").listFiles()?.map { it.name } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        if (sysBlockResult.contains("sda") || File("/dev/block/sda").exists()) {
            foundDisks.add("/dev/block/sda")
        }
        if (sysBlockResult.contains("mmcblk0") || File("/dev/block/mmcblk0").exists()) {
            foundDisks.add("/dev/block/mmcblk0")
        }
        if (sysBlockResult.contains("nvme0n1") || File("/dev/block/nvme0n1").exists()) {
            foundDisks.add("/dev/block/nvme0n1")
        }

        return foundDisks
    }

    fun findPartitionBlockPath(partitionName: String): String? {
        val candidateLocations = listOf(
            "/dev/block/bootdevice/by-name/$partitionName",
            "/dev/block/by-name/$partitionName",
            "/dev/block/platform/soc/*/by-name/$partitionName",
            "/dev/block/platform/soc.0/*/by-name/$partitionName"
        )

        for (location in candidateLocations) {
            if (location.contains("*")) {
                val resolved = resolveGlobPath(location)
                if (resolved != null && File(resolved).exists()) {
                    return resolved
                }
            } else if (File(location).exists()) {
                return location
            }
        }
        return null
    }

    private fun resolveGlobPath(pattern: String): String? {
        return try {
            val parts = pattern.split("*")
            if (parts.size == 2) {
                val parent = File(parts[0])
                if (parent.exists() && parent.isDirectory) {
                    val match = parent.listFiles()?.firstOrNull()
                    if (match != null) {
                        val fullPath = match.absolutePath + parts[1]
                        if (File(fullPath).exists()) return fullPath
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Dynamically inspects all overloads of RecoverySystem.rebootWipeUserData
     * to prevent IllegalArgumentException / argument count mismatch crashes.
     */
    fun triggerPlatformRecoveryWipe(context: Context, reason: String): Boolean {
        return try {
            Log.i(TAG, "Invoking RecoverySystem.rebootWipeUserData via platform authority...")
            val recoverySystemClass = RecoverySystem::class.java
            val methods = recoverySystemClass.declaredMethods.filter { it.name == "rebootWipeUserData" }

            for (method in methods) {
                method.isAccessible = true
                val types = method.parameterTypes

                try {
                    when (types.size) {
                        5 -> {
                            // rebootWipeUserData(Context, boolean shutdown, String reason, boolean force, boolean wipeEuicc)
                            method.invoke(null, context, false, reason, true, false)
                            return true
                        }
                        4 -> {
                            // rebootWipeUserData(Context, boolean shutdown, String reason, boolean force)
                            method.invoke(null, context, false, reason, true)
                            return true
                        }
                        3 -> {
                            // rebootWipeUserData(Context, boolean shutdown, String reason)
                            if (types[1] == Boolean::class.javaPrimitiveType) {
                                method.invoke(null, context, false, reason)
                                return true
                            }
                        }
                        2 -> {
                            // rebootWipeUserData(Context, String reason)
                            if (types[1] == String::class.java) {
                                method.invoke(null, context, reason)
                                return true
                            }
                        }
                    }
                } catch (invEx: Exception) {
                    Log.w(TAG, "Reflection attempt failed on overload (${types.size} params): ${invEx.message}")
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Platform wipe failed: ${e.message}", e)
            false
        }
    }

    suspend fun executeKernelRebootFallback() {
        Log.e(TAG, "Executing immediate hardware reboot fallback...")
        if (RootChecker.isDeviceRooted()) {
            // Stage sync and reboot directly
            RootExecutor.run("sync")
            RootExecutor.run("reboot recovery")
            RootExecutor.run("reboot bootloader")
            RootExecutor.run("reboot -f")

            // Enable SysRq before triggering kernel panic reset
            RootExecutor.run("echo 1 > /proc/sys/kernel/sysrq")
            RootExecutor.run("echo c > /proc/sysrq-trigger")
        } else {
            try {
                Runtime.getRuntime().exec(arrayOf("reboot", "recovery"))
            } catch (_: Exception) {}
        }
    }
}