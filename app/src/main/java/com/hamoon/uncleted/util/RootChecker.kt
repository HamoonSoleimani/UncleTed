package com.hamoon.uncleted.util

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootChecker {

    private const val TAG = "RootChecker"

    enum class RootProvider {
        MAGISK,
        KERNEL_SU,
        APATCH,
        GENERIC_SU,
        NONE
    }

    @Volatile
    private var isRooted: Boolean? = null

    @Volatile
    private var detectedProvider: RootProvider? = null

    suspend fun isDeviceRooted(): Boolean {
        if (isRooted != null) {
            return isRooted as Boolean
        }

        return withContext(Dispatchers.IO) {
            if (isRooted != null) {
                return@withContext isRooted as Boolean
            }

            Log.d(TAG, "Starting universal root detection across Magisk, KernelSU, APatch...")
            val check = checkExecution() || checkKnownBinaries() || checkProviderFilesystems()
            isRooted = check
            Log.d(TAG, "Root detection complete. Result: $check (Provider: $detectedProvider)")
            check
        }
    }

    suspend fun getRootProvider(): RootProvider {
        if (detectedProvider != null) return detectedProvider!!
        isDeviceRooted()
        return detectedProvider ?: RootProvider.NONE
    }

    /**
     * Primary Check: Test root shell execution directly via 'su -c id'.
     * Works on Magisk, KernelSU, KernelSU-Next, and APatch once granted.
     */
    private suspend fun checkExecution(): Boolean {
        return try {
            val result = RootExecutor.run("id")
            val hasRootUid = result.isSuccess && result.output.any { it.contains("uid=0(root)") }
            if (hasRootUid) {
                identifyProvider()
            }
            hasRootUid
        } catch (e: Exception) {
            Log.d(TAG, "Root execution check failed: ${e.message}")
            false
        }
    }

    private suspend fun identifyProvider() {
        // 1. KernelSU / KernelSU-Next Identification
        val ksuCheck = RootExecutor.run("which ksud")
        val ksuDir = File("/data/adb/ksu").exists()
        val kernelVersion = RootExecutor.run("uname -r").output.firstOrNull() ?: ""

        if (ksuCheck.isSuccess || ksuDir || kernelVersion.contains("KernelSU", ignoreCase = true)) {
            detectedProvider = RootProvider.KERNEL_SU
            Log.i(TAG, "Root Environment: KernelSU / KernelSU-Next detected.")
            return
        }

        // 2. APatch Identification
        val apatchCheck = RootExecutor.run("which apd")
        val apatchDir = File("/data/adb/ap").exists()
        if (apatchCheck.isSuccess || apatchDir) {
            detectedProvider = RootProvider.APATCH
            Log.i(TAG, "Root Environment: APatch detected.")
            return
        }

        // 3. Magisk Identification
        val magiskCheck = RootExecutor.run("which magisk")
        val magiskDir = File("/data/adb/magisk").exists()
        if (magiskCheck.isSuccess || magiskDir) {
            detectedProvider = RootProvider.MAGISK
            Log.i(TAG, "Root Environment: Magisk detected.")
            return
        }

        detectedProvider = RootProvider.GENERIC_SU
        Log.i(TAG, "Root Environment: Generic SU detected.")
    }

    /**
     * Secondary Check: Known binary paths across legacy and modern root managers.
     */
    private fun checkKnownBinaries(): Boolean {
        val paths = arrayOf(
            // KernelSU & APatch paths
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/system/bin/ksud",
            // Magisk & Standard SU paths
            "/data/adb/magisk/busybox",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )

        for (path in paths) {
            if (File(path).exists()) {
                Log.d(TAG, "Found root binary at: $path")
                return true
            }
        }
        return false
    }

    /**
     * Tertiary Check: Filesystem markers for modern systemless root environments.
     */
    private fun checkProviderFilesystems(): Boolean {
        val rootDirs = arrayOf(
            "/data/adb/modules",
            "/data/adb/ksu",
            "/data/adb/ap",
            "/data/adb/magisk"
        )

        return rootDirs.any { File(it).exists() }
    }

    fun clearCache() {
        isRooted = null
        detectedProvider = null
        Log.d(TAG, "Root detection cache cleared")
    }
}