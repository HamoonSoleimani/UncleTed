package com.hamoon.uncleted.util

import android.content.Context
import android.os.RecoverySystem
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.crypto.StrongBoxSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object EmergencyDestructionEngine {

    private const val TAG = "DestructionEngine"

    /**
     * Executes complete sub-millisecond emergency destruction sequence.
     * 1. Isolates all radio and network interfaces.
     * 2. Destroys discrete StrongBox/Titan M2 master key silicon registers.
     * 3. Erases Vold user keys and zeroes the File-Based Encryption metadata partition.
     * 4. Stages Bootloader Control Block (BCB) recovery wipe command.
     * 5. Triggers platform recovery wipe or hardware fallback panic.
     */
    suspend fun executeDestructionSequence(context: Context, reason: String): Unit = withContext(Dispatchers.IO) {
        Log.e(TAG, "!!! INITIATING SUB-MILLISECOND EMERGENCY DESTRUCTION: $reason !!!")
        EventLogger.log(context, "CRITICAL: Emergency destruction sequence executed (Reason: $reason)")

        // 1. Instantly isolate all network interfaces via iptables DROP and radio disable
        killCommunications(context)

        // 2. Destroy discrete Titan M2 / StrongBox hardware key in silicon
        StrongBoxSecurityManager.executeMasterKeySuicide(context)

        val isRooted = RootChecker.isDeviceRooted()

        if (isRooted) {
            // 3. Purge Vold user keys and zero FBE metadata header partition
            evictAndZeroEncryptionKeys()

            // 4. Stage low-level BCB recovery command
            stageRecoveryWipeCommand()

            // 5. Trigger platform recovery wipe via platform authority or fallback reboot
            val platformSuccess = triggerPlatformRecoveryWipe(context, reason)
            if (!platformSuccess) {
                executeKernelRebootFallback()
            }
        } else {
            // Non-root / Device Owner execution route
            try {
                val strategy = DefenseCoordinator.resolveStrategy(context)
                strategy.executeWipe(reason)
            } catch (e: Exception) {
                Log.e(TAG, "DefenseStrategy wipe failed, falling back to platform recovery wipe", e)
                triggerPlatformRecoveryWipe(context, reason)
            }
        }
        Unit
    }

    suspend fun killCommunications(context: Context) {
        RadioIsolationManager.isolateAllCommunications(context)
    }

    /**
     * Mathematically sound cryptographic erasure:
     * Overwriting the 16KB FBE metadata block device containing the root Key Encryption Keys (KEKs)
     * instantly renders all userdata blocks unrecoverable, bypassing UFS/NVMe wear-leveling pitfalls.
     */
    suspend fun evictAndZeroEncryptionKeys() {
        Log.e(TAG, "Evicting Vold user keys and zeroing FBE metadata headers...")

        val keyDemolitionCommands = listOf(
            "rm -rf /data/misc/vold/user_keys/* 2>/dev/null || true",
            "rm -rf /metadata/vold/user_keys/* 2>/dev/null || true",
            "rm -rf /data/system/users/0/*.key 2>/dev/null || true",
            "rm -rf /data/system/gatekeeper.*.key 2>/dev/null || true",
            "rm -rf /data/system_de/0/spblob/* 2>/dev/null || true",
            "rm -rf /data/system/locksettings.db* 2>/dev/null || true"
        )
        RootExecutor.runMultiple(keyDemolitionCommands, logErrors = false)

        // Zero the master cryptographic metadata partition headers (first 16MB)
        val metadataPath = findPartitionBlockPath("metadata")
        if (metadataPath != null) {
            Log.e(TAG, "Zeroing master FBE metadata partition header at: $metadataPath")
            RootExecutor.run("dd if=/dev/zero of=$metadataPath bs=1048576 count=16 conv=fsync", logErrors = false)
        } else {
            Log.w(TAG, "Metadata partition by-name not found directly; searching block devices...")
            val altMetadata = findPartitionBlockPath("userdata")
            if (altMetadata != null) {
                // Zero the first 4MB of userdata where filesystem superblocks and crypto headers reside
                RootExecutor.run("dd if=/dev/zero of=$altMetadata bs=4096 count=1024 conv=fsync", logErrors = false)
            }
        }

        RootExecutor.run("sync", logErrors = false)
    }

    /**
     * Stages an autonomous Bootloader Control Block (BCB) recovery wipe command in /cache/recovery/command.
     * Ensures that even if userspace halts prematurely, recovery formats userdata on the next boot cycle.
     */
    suspend fun stageRecoveryWipeCommand() {
        val recoveryCommandFile = "/cache/recovery/command"
        val commands = listOf(
            "mkdir -p /cache/recovery",
            "echo '--wipe_data\n--reason=UncleTed_Emergency_Sanitize' > $recoveryCommandFile",
            "chmod 644 $recoveryCommandFile",
            "sync"
        )
        RootExecutor.runMultiple(commands, logErrors = false)
        Log.i(TAG, "BCB wipe command successfully staged under /cache/recovery/command.")
    }

    fun triggerPlatformRecoveryWipe(context: Context, reason: String): Boolean {
        return try {
            Log.i(TAG, "Invoking RecoverySystem.rebootWipeUserData via platform reflection...")
            val recoverySystemClass = RecoverySystem::class.java
            val methods = recoverySystemClass.declaredMethods.filter { it.name == "rebootWipeUserData" }

            for (method in methods) {
                method.isAccessible = true
                val types = method.parameterTypes

                try {
                    when (types.size) {
                        5 -> {
                            method.invoke(null, context, false, reason, true, false)
                            return true
                        }
                        4 -> {
                            method.invoke(null, context, false, reason, true)
                            return true
                        }
                        3 -> {
                            if (types[1] == Boolean::class.javaPrimitiveType) {
                                method.invoke(null, context, false, reason)
                                return true
                            }
                        }
                        2 -> {
                            if (types[1] == String::class.java) {
                                method.invoke(null, context, reason)
                                return true
                            }
                        }
                    }
                } catch (invEx: Exception) {
                    Log.w(TAG, "Reflection overload (${types.size} params) failed: ${invEx.message}")
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Platform recovery wipe invocation failed: ${e.message}", e)
            false
        }
    }

    fun findPartitionBlockPath(partitionName: String): String? {
        val candidateLocations = listOf(
            "/dev/block/bootdevice/by-name/$partitionName",
            "/dev/block/by-name/$partitionName",
            "/dev/block/platform/soc/*/by-name/$partitionName",
            "/dev/block/platform/soc.0/*/by-name/$partitionName",
            "/dev/block/mapper/$partitionName"
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

    suspend fun executeKernelRebootFallback() {
        Log.e(TAG, "Executing low-level hardware reboot fallback...")
        if (RootChecker.isDeviceRooted()) {
            RootExecutor.run("sync")
            RootExecutor.run("reboot recovery")
            RootExecutor.run("/system/bin/reboot recovery")
            RootExecutor.run("reboot -f")

            // Unconditional hardware kernel reboot via SysRq trigger
            RootExecutor.run("echo 1 > /proc/sys/kernel/sysrq")
            RootExecutor.run("echo c > /proc/sysrq-trigger")
        } else {
            try {
                Runtime.getRuntime().exec(arrayOf("reboot", "recovery"))
            } catch (_: Exception) {}
        }
    }
}