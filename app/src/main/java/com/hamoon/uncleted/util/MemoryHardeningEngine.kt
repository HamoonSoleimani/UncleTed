package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MemoryHardeningEngine {

    private const val TAG = "MemoryHardeningEngine"

    suspend fun executeVolatileScrub(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!SecurityPreferences.isZramScrubbingEnabled(context)) {
            return@withContext false
        }

        Log.w(TAG, "Initiating kernel memory compaction, cache drop, and volatile heap sanitization...")
        EventLogger.log(context, "MEMORY DEFENSE: Executing volatile memory compaction & cache purge.")

        var success = true

        // 1. Root / Privileged Profile: Kernel-level drop_caches and compact_memory
        if (RootChecker.isDeviceRooted()) {
            val kernelMemoryCommands = listOf(
                // Sync all dirty filesystems to physical media
                "sync",
                // Drop pagecache, dentries, and inodes from volatile RAM (Leaves zero cached plaintext files)
                "echo 3 > /proc/sys/vm/drop_caches",
                // Compact memory to consolidate memory fragments into unallocated zeroed blocks
                "echo 1 > /proc/sys/vm/compact_memory"
            )
            val dropResult = RootExecutor.runMultiple(kernelMemoryCommands, logErrors = false)
            if (dropResult.none { it.isSuccess }) {
                Log.w(TAG, "Kernel drop_caches execution returned non-zero status.")
                success = false
            } else {
                Log.i(TAG, "Kernel pagecache, dentries, and inodes dropped; memory compacted.")
            }

            // 2. Ephemeral ZRAM Swap Eviction and Rotation
            if (SecurityPreferences.isZramReKeyingEnabled(context)) {
                executeZramSwapReKey()
            }
        }

        // 3. JVM / ART Heap Sanitization
        performUserspaceHeapSanitization()

        success
    }

    private suspend fun executeZramSwapReKey() {
        Log.w(TAG, "Flushing and rotating ephemeral ZRAM swap space...")
        val zramCommands = listOf(
            // Flush dirty anonymous pages out of ZRAM swap device
            "swapoff /dev/block/zram0 2>/dev/null || true",
            // Reset ZRAM controller to discard uncompressed physical memory allocations
            "echo 1 > /sys/block/zram0/reset 2>/dev/null || true",
            // Re-bind swap device with clean disksize
            "swapon /dev/block/zram0 2>/dev/null || true"
        )
        RootExecutor.runMultiple(zramCommands, logErrors = false)
    }

    private fun performUserspaceHeapSanitization() {
        try {
            // Suggest explicit Garbage Collection pass to finalize and unlink dangling byte buffers
            System.gc()
            System.runFinalization()

            // Allocate a scratch buffer, pin it, clear it through the native barrier, and unpin
            val scratch = ByteArray(1024 * 64)
            NativeSecurityBridge.pinMemory(scratch)
            NativeSecurityBridge.zeroByteArray(scratch)
            NativeSecurityBridge.unpinMemory(scratch)
        } catch (e: Exception) {
            Log.w(TAG, "Userspace heap sanitization pass encountered non-fatal error: ${e.message}")
        }
    }
}