package com.hamoon.uncleted.util

import android.content.Context
import android.os.Process
import android.os.RecoverySystem
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Method

/**
 * Low-level hardware destruction and cryptographic eviction engine.
 * Resolves storage architectural differences (UFS vs. eMMC) and guarantees
 * that platform wipes and low-level block zeroing do not corrupt each other's execution paths.
 */
object EmergencyDestructionEngine {

    private const val TAG = "DestructionEngine"

    /**
     * Executes the destruction pipeline according to active privileges.
     * If platform wipe authority is present, platform recovery wipe is initiated directly.
     * If low-level cryptographic zeroing is required, block headers are destroyed followed
     * by an immediate hardware bootloader reset (avoiding broken userspace calls on zeroed partitions).
     */
    suspend fun executeDestructionSequence(context: Context, reason: String) = withContext(Dispatchers.IO) {
        Log.e(TAG, "!!! INITIATING EMERGENCY DESTRUCTION SEQUENCE: $reason !!!")

        // 1. Cut all network communications immediately
        killCommunications()

        val isRooted = RootChecker.isDeviceRooted()

        if (isRooted && SecurityPreferences.isSecureWipeEnabled(context)) {
            // Low-level cryptographic and block destruction path
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

    /**
     * Cuts all inbound and outbound traffic via iptables to prevent forensic capture or aborts.
     */
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
     * Destroys File-Based Encryption (FBE) keys and Vold metadata headers in sub-second time.
     */
    suspend fun evictAndZeroEncryptionKeys() {
        Log.e(TAG, "Evicting kernel keyrings and zeroing Vold user keys...")

        val destructionCommands = listOf(
            // Evict Linux Kernel Keyrings
            "keyctl clear @u",
            "keyctl clear @s",

            // Zero out Vold user key directories
            "rm -rf /data/misc/vold/user_keys/*",
            "rm -rf /metadata/vold/user_keys/*",
            "rm -rf /data/system/users/0/*.key",

            // Overwrite Vold metadata keys
            "find /metadata/vold/ -type f -exec dd if=/dev/zero of={} bs=4096 count=10 conv=fsync \\;"
        )

        RootExecutor.runMultiple(destructionCommands)

        // Zero out metadata partition if resolved
        val metadataPath = findPartitionBlockPath("metadata")
        if (metadataPath != null) {
            RootExecutor.run("dd if=/dev/zero of=$metadataPath bs=1048576 count=10 conv=fsync")
        }
    }

    /**
     * Dynamically identifies whether the device runs UFS (/dev/block/sd*) or eMMC (/dev/block/mmcblk*)
     * and zeroes partition table headers without hardcoding obsolete storage device paths.
     */
    suspend fun destroyPrimaryBlockHeaders() {
        val targets = mutableListOf<String>()

        // 1. Target key partition nodes dynamically
        findPartitionBlockPath("metadata")?.let { targets.add(it) }
        findPartitionBlockPath("userdata")?.let { targets.add(it) }

        for (path in targets) {
            RootExecutor.run("dd if=/dev/zero of=$path bs=1048576 count=20 conv=fsync")
        }

        // 2. Locate the primary disk device dynamically (UFS vs. eMMC vs. NVMe)
        val primaryDisks = detectStorageDisks()
        for (disk in primaryDisks) {
            Log.e(TAG, "Overwriting primary block device headers on: $disk")
            RootExecutor.run("dd if=/dev/zero of=$disk bs=4096 count=4096 conv=fsync")
        }
    }

    /**
     * Discovers physical block devices across UFS, eMMC, and NVMe platforms.
     */
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

    /**
     * Resolves partition device nodes dynamically across Qualcomm, MediaTek, Exynos, and Tensor layouts.
     */
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
     * Invokes RecoverySystem.rebootWipeUserData with MASTER_CLEAR platform authority.
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
     * Low-level reboot execution when storage partitions are no longer in a mountable state.
     */
    suspend fun executeKernelRebootFallback() {
        Log.e(TAG, "Executing immediate hardware reboot fallback...")
        if (RootChecker.isDeviceRooted()) {
            RootExecutor.run("reboot bootloader")
            RootExecutor.run("reboot recovery")
            RootExecutor.run("reboot -f")
            RootExecutor.run("echo c > /proc/sysrq-trigger")
        } else {
            try {
                Runtime.getRuntime().exec(arrayOf("reboot", "recovery"))
            } catch (_: Exception) {}
        }
    }
}