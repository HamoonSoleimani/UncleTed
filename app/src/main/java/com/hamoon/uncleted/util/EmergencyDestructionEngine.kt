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
     * Executes complete sub-millisecond emergency destruction sequence:
     * 1. Isolates all radio and network interfaces.
     * 2. Destroys discrete StrongBox/Titan M2 master key silicon registers.
     * 3. Erases Vold user keys and synthetic password blobs.
     * 4. Issues root-level JEDEC BLKSECDISCARD / BLKDISCARD to metadata partitions.
     * 5. Stages Bootloader Control Block (BCB) recovery wipe command.
     * 6. Triggers recovery wipe or hardware reboot fallback.
     */
    suspend fun executeDestructionSequence(context: Context, reason: String): Unit = withContext(Dispatchers.IO) {
        Log.e(TAG, "!!! INITIATING EMERGENCY DESTRUCTION: $reason !!!")
        EventLogger.log(context, "CRITICAL: Emergency destruction sequence executed (Reason: $reason)")

        // 1. Isolate network and radio interfaces
        killCommunications(context)

        // 2. Destroy discrete Titan M2 / StrongBox hardware key in silicon
        StrongBoxSecurityManager.executeMasterKeySuicide(context)

        val isRooted = RootChecker.isDeviceRooted()

        if (isRooted) {
            // 3. Purge Vold user keys and execute root-level silicon sanitize
            evictAndZeroEncryptionKeys()

            // 4. Stage low-level BCB recovery command
            stageRecoveryWipeCommand()

            // 5. Trigger platform recovery wipe or hardware fallback reboot
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
                Log.e(TAG, "DefenseStrategy wipe failed, falling back to platform recovery wipe: ${e.message}", e)
                triggerPlatformRecoveryWipe(context, reason)
            }
        }
        Unit
    }

    suspend fun killCommunications(context: Context) {
        RadioIsolationManager.isolateAllCommunications(context)
    }

    /**
     * Deletes Vold user keys and issues hardware discard commands directly via elevated root shell.
     * Bypasses userspace SELinux sandbox restrictions on raw block devices.
     */
    suspend fun evictAndZeroEncryptionKeys() {
        Log.e(TAG, "Evicting Vold user keys and executing JEDEC silicon-level hardware sanitize...")

        val keyDemolitionCommands = listOf(
            "rm -rf /data/misc/vold/user_keys/* 2>/dev/null || true",
            "rm -rf /metadata/vold/user_keys/* 2>/dev/null || true",
            "rm -rf /data/system/users/0/*.key 2>/dev/null || true",
            "rm -rf /data/system/gatekeeper.*.key 2>/dev/null || true",
            "rm -rf /data/system_de/0/spblob/* 2>/dev/null || true",
            "rm -rf /data/system/locksettings.db* 2>/dev/null || true"
        )
        RootExecutor.runMultiple(keyDemolitionCommands, logErrors = false)

        val metadataPath = findPartitionBlockPath("metadata") ?: "/dev/block/by-name/metadata"

        // Execute block discard via root domain to bypass untrusted_app SELinux denials
        val discardCommands = listOf(
            "blkdiscard -s $metadataPath 2>/dev/null || blkdiscard $metadataPath 2>/dev/null || true",
            "dd if=/dev/zero of=$metadataPath bs=1048576 count=16 conv=fsync 2>/dev/null || true",
            "sync"
        )
        RootExecutor.runMultiple(discardCommands, logErrors = false)

        // Secondary: invoke native JNI if running in a privileged domain
        if (NativeSecurityBridge.isNativeLoaded()) {
            NativeSecurityBridge.executeSiliconDiscard(metadataPath)
        }
    }

    suspend fun stageRecoveryWipeCommand() {
        val recoveryCommandFile = "/cache/recovery/command"
        val commands = listOf(
            "mkdir -p /cache/recovery",
            "echo '--wipe_data\n--reason=UncleTed_Emergency_Sanitize' > $recoveryCommandFile",
            "chmod 644 $recoveryCommandFile",
            "sync"
        )
        RootExecutor.runMultiple(commands, logErrors = false)
        Log.i(TAG, "BCB wipe command staged under /cache/recovery/command.")
    }

    fun triggerPlatformRecoveryWipe(context: Context, reason: String): Boolean {
        return try {
            Log.i(TAG, "Invoking RecoverySystem.rebootWipeUserData via reflection...")
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