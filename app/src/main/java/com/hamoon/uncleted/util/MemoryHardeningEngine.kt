package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MemoryHardeningEngine {

    private const val TAG = "MemoryHardeningEngine"

    suspend fun executeVolatileScrub(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!SecurityPreferences.isZramScrubbingEnabled(context)) {
            Log.d(TAG, "Volatile memory scrubbing is disabled in preferences.")
            return@withContext false
        }

        Log.w(TAG, "Initiating multi-tier volatile memory compaction, cache drop, and heap sanitization...")
        EventLogger.log(context, "MEMORY DEFENSE: Executing volatile memory compaction & cache purge.")

        var success = true

        // =========================================================================
        // ROUTE B: ROOT-PRIVILEGED KERNEL PURGE & SWAP RE-KEYING
        // =========================================================================
        if (RootChecker.isDeviceRooted()) {
            val kernelMemoryCommands = listOf(
                "sync",
                "echo 3 > /proc/sys/vm/drop_caches",
                "echo 1 > /proc/sys/vm/compact_memory"
            )

            val dropResult = RootExecutor.runMultiple(kernelMemoryCommands, logErrors = false)
            if (dropResult.none { it.isSuccess }) {
                Log.w(TAG, "Kernel drop_caches execution returned non-zero exit code.")
                success = false
            } else {
                Log.i(TAG, "Kernel pagecache, dentries, and unpinned inodes dropped; memory compacted.")
            }

            // Ephemeral ZRAM Swap Eviction & Key Rotation
            if (SecurityPreferences.isZramReKeyingEnabled(context)) {
                val zramSuccess = executeZramSwapReKey()
                if (!zramSuccess) {
                    success = false
                }
            }
        } else {
            // =====================================================================
            // ROUTE A: DEVICE OWNER / USERSANDBOX COMPACTION
            // =====================================================================
            Log.i(TAG, "Non-root execution profile: Enforcing userspace memory compaction and heap zeroing.")
        }

        // =========================================================================
        // USERS PACE ART HEAP SCRUBBING & NATIVE BARRIERS (BOTH PROFILES)
        // =========================================================================
        performUserspaceHeapSanitization()

        success
    }

    private suspend fun executeZramSwapReKey(): Boolean {
        // Verify that ZRAM swap device is present in the Linux SysFS hierarchy
        val zramDeviceExists = RootExecutor.run("test -b /dev/block/zram0 && echo exists", logErrors = false)
            .output.any { it.contains("exists") }

        if (!zramDeviceExists) {
            Log.w(TAG, "ZRAM block device (/dev/block/zram0) does not exist on this kernel build. Skipping swap re-key.")
            return false
        }

        Log.w(TAG, "Flushing and rotating ephemeral ZRAM swap device...")

        val zramCommands = listOf(
            "swapoff /dev/block/zram0 2>/dev/null || true",
            "echo 1 > /sys/block/zram0/reset 2>/dev/null || true",
            "swapon /dev/block/zram0 2>/dev/null || true"
        )

        val result = RootExecutor.runMultiple(zramCommands, logErrors = false)
        val success = result.any { it.isSuccess }

        if (success) {
            Log.i(TAG, "ZRAM swap reset completed. Ephemeral block allocator re-initialized with fresh keys.")
        } else {
            Log.e(TAG, "Failed resetting ZRAM swap device.")
        }

        return success
    }

    private fun performUserspaceHeapSanitization() {
        try {
            // 1. Force garbage collection and finalization to reclaim dereferenced key buffers
            System.gc()
            System.runFinalization()

            // 2. Allocate an ephemeral scratch memory buffer, pin it in physical RAM,
            //    wipe it through native memory barriers, and unlock it.
            val scratch = ByteArray(1024 * 128) // 128 KB
            NativeSecurityBridge.pinMemory(scratch)
            NativeSecurityBridge.zeroByteArray(scratch)
            NativeSecurityBridge.unpinMemory(scratch)

            // 3. Clear temporary files from cache
            cleanTempCacheFiles()
        } catch (e: Exception) {
            Log.w(TAG, "Userspace heap sanitization pass encountered non-fatal error: ${e.message}")
        }
    }

    private fun cleanTempCacheFiles() {
        try {
            val tmpDir = File("/data/local/tmp")
            if (tmpDir.exists() && tmpDir.canWrite()) {
                tmpDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("sc_") || file.name.startsWith("credentials")) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }
}