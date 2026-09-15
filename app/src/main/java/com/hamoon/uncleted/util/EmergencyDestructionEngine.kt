package com.hamoon.uncleted.util

import android.content.Context
import android.os.RecoverySystem
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object EmergencyDestructionEngine {

    private const val TAG = "DestructionEngine"

    suspend fun executeDestructionSequence(context: Context, reason: String): Unit = withContext(Dispatchers.IO) {
        Log.e(TAG, "!!! INITIATING EMERGENCY DESTRUCTION SEQUENCE: $reason !!!")

        // 1. Instantly isolate all network interfaces via iptables DROP
        killCommunications()

        val isRooted = RootChecker.isDeviceRooted()

        if (isRooted) {
            evictAndZeroEncryptionKeys()
            destroyPrimaryBlockHeaders()
            stageRecoveryWipeCommand()

            val platformSuccess = triggerPlatformRecoveryWipe(context, reason)
            if (!platformSuccess) {
                executeKernelRebootFallback()
            }
        } else {
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
                RootExecutor.runMultiple(dropCommands, logErrors = false)
                Log.i(TAG, "Network firewall killswitch active: all traffic dropped.")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed isolating network interfaces: ${t.message}")
        }
    }

    suspend fun evictAndZeroEncryptionKeys() {
        Log.e(TAG, "Evicting Vold user keys and cryptographic credentials...")

        val keyDemolitionCommands = listOf(
            "rm -rf /data/misc/vold/user_keys/* 2>/dev/null || true",
            "rm -rf /metadata/vold/user_keys/* 2>/dev/null || true",
            "rm -rf /data/system/users/0/*.key 2>/dev/null || true",
            "rm -rf /data/system/gatekeeper.*.key 2>/dev/null || true",
            "rm -rf /data/system_de/0/spblob/* 2>/dev/null || true",
            "rm -rf /data/system/locksettings.db* 2>/dev/null || true"
        )
        RootExecutor.runMultiple(keyDemolitionCommands, logErrors = false)

        val metadataPath = findPartitionBlockPath("metadata")
        if (metadataPath != null) {
            RootExecutor.run("dd if=/dev/zero of=$metadataPath bs=1048576 count=16 conv=fsync", logErrors = false)
        }

        RootExecutor.run("sync", logErrors = false)
    }

    /**
     * Low-level GPT / Master Partition Table Destruction (Level 4 Nuclear Winter).
     */
    suspend fun destroyPrimaryBlockHeaders() {
        val primaryDisks = detectStorageDisks()
        for (disk in primaryDisks) {
            Log.e(TAG, "Overwriting primary block device headers on: $disk")
            RootExecutor.run("dd if=/dev/zero of=$disk bs=4096 count=2048 conv=fsync", logErrors = false)
        }
        RootExecutor.run("sync", logErrors = false)
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

    suspend fun stageRecoveryWipeCommand() {
        val recoveryCommandFile = "/cache/recovery/command"
        val commands = listOf(
            "mkdir -p /cache/recovery",
            "echo '--wipe_data\n--reason=UncleTed_Emergency_Sanitize' > $recoveryCommandFile",
            "chmod 644 $recoveryCommandFile",
            "sync"
        )
        RootExecutor.runMultiple(commands, logErrors = false)
    }

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

    suspend fun executeKernelRebootFallback() {
        Log.e(TAG, "Executing low-level hardware reboot fallback...")
        if (RootChecker.isDeviceRooted()) {
            RootExecutor.run("sync")
            RootExecutor.run("reboot recovery")
            RootExecutor.run("/system/bin/reboot recovery")
            RootExecutor.run("reboot -f")

            RootExecutor.run("echo 1 > /proc/sys/kernel/sysrq")
            RootExecutor.run("echo c > /proc/sysrq-trigger")
        } else {
            try {
                Runtime.getRuntime().exec(arrayOf("reboot", "recovery"))
            } catch (_: Exception) {}
        }
    }
}