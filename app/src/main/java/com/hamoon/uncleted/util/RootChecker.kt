package com.hamoon.uncleted.util

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

            Log.d(TAG, "Starting functional root execution test...")
            val canExecuteRoot = checkExecution()
            isRooted = canExecuteRoot
            Log.d(TAG, "Root validation complete. Result: $canExecuteRoot (Provider: $detectedProvider)")
            canExecuteRoot
        }
    }

    suspend fun getRootProvider(): RootProvider {
        if (detectedProvider != null) return detectedProvider!!
        isDeviceRooted()
        return detectedProvider ?: RootProvider.NONE
    }

    private suspend fun checkExecution(): Boolean {
        return try {
            val result = RootExecutor.run("id", logErrors = false)
            val hasRootUid = result.isSuccess && result.output.any { it.contains("uid=0(root)") }
            if (hasRootUid) {
                identifyProvider()
            } else {
                detectedProvider = RootProvider.NONE
            }
            hasRootUid
        } catch (e: Exception) {
            Log.d(TAG, "Root execution check failed: ${e.message}")
            detectedProvider = RootProvider.NONE
            false
        }
    }

    private suspend fun identifyProvider() {
        // 1. Magisk Identification (Fast filesystem check first to prevent probing non-installed tools)
        val magiskDir = File("/data/adb/magisk").exists()
        val magiskCheck = if (magiskDir) true else RootExecutor.run("command -v magisk >/dev/null 2>&1", logErrors = false).isSuccess
        if (magiskDir || magiskCheck) {
            detectedProvider = RootProvider.MAGISK
            Log.i(TAG, "Root Environment: Magisk active.")
            return
        }

        // 2. KernelSU / KernelSU-Next Identification
        val ksuDir = File("/data/adb/ksu").exists()
        val kernelVersion = RootExecutor.run("uname -r", logErrors = false).output.firstOrNull() ?: ""
        val ksuCheck = if (ksuDir) true else RootExecutor.run("command -v ksud >/dev/null 2>&1", logErrors = false).isSuccess

        if (ksuCheck || ksuDir || kernelVersion.contains("KernelSU", ignoreCase = true)) {
            detectedProvider = RootProvider.KERNEL_SU
            Log.i(TAG, "Root Environment: KernelSU / KernelSU-Next active.")
            return
        }

        // 3. APatch Identification
        val apatchDir = File("/data/adb/ap").exists()
        val apatchCheck = if (apatchDir) true else RootExecutor.run("command -v apd >/dev/null 2>&1", logErrors = false).isSuccess
        if (apatchCheck || apatchDir) {
            detectedProvider = RootProvider.APATCH
            Log.i(TAG, "Root Environment: APatch active.")
            return
        }

        detectedProvider = RootProvider.GENERIC_SU
        Log.i(TAG, "Root Environment: Generic SU active.")
    }

    fun clearCache() {
        isRooted = null
        detectedProvider = null
        Log.d(TAG, "Root detection cache cleared")
    }
}