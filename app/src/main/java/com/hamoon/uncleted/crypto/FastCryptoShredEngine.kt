package com.hamoon.uncleted.crypto

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.NativeSecurityBridge
import com.hamoon.uncleted.util.RootChecker
import com.hamoon.uncleted.util.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

object FastCryptoShredEngine {

    private const val TAG = "FastCryptoShred"

    suspend fun executeSubMillisecondHeaderPurge(context: Context): Boolean = withContext(Dispatchers.IO) {
        Log.e(TAG, "!!! INITIATING TRUE FBE CRYPTO-SHREDDING PIPELINE !!!")
        EventLogger.log(context, "CRITICAL: Executing File-Based Encryption key purge and metadata block zeroing.")

        // 1. Destroy Titan M2 / StrongBox master key in silicon
        StrongBoxSecurityManager.executeMasterKeySuicide(context)

        // 2. Evaporate volatile in-memory keys
        EphemeralKeyDecayEngine.purgeEphemeralKey(context)

        // 3. Purge Android's actual FBE Key Directories
        if (RootChecker.isDeviceRooted()) {
            val fbeKeyPurgeCommands = listOf(
                "rm -rf /data/misc/vold/user_keys/* 2>/dev/null || true",
                "rm -rf /metadata/vold/user_keys/* 2>/dev/null || true",
                "rm -rf /data/system_de/0/spblob/* 2>/dev/null || true",
                "rm -rf /data/system/gatekeeper.*.key 2>/dev/null || true",
                "rm -rf /data/system/users/0/*.key 2>/dev/null || true",
                "rm -rf /data/system/locksettings.db* 2>/dev/null || true"
            )
            RootExecutor.runMultiple(fbeKeyPurgeCommands, logErrors = false)
        }

        // 4. Overwrite and discard the 64KB Metadata Encryption Key Block
        val metadataPartitions = listOf(
            "/dev/block/by-name/metadata",
            "/dev/block/bootdevice/by-name/metadata",
            "/dev/block/platform/soc/*/by-name/metadata"
        )

        for (path in metadataPartitions) {
            val file = File(path)
            if (file.exists()) {
                zeroFillMetadataKeyBlock(path)
                if (NativeSecurityBridge.isNativeLoaded()) {
                    NativeSecurityBridge.executeSiliconDiscard(path)
                }
            }
        }

        if (RootChecker.isDeviceRooted()) {
            val discardCmds = listOf(
                "dd if=/dev/zero of=/dev/block/by-name/metadata bs=4096 count=16 conv=fsync 2>/dev/null || true",
                "blkdiscard -s /dev/block/by-name/metadata 2>/dev/null || blkdiscard /dev/block/by-name/metadata 2>/dev/null || true",
                "sync",
                "echo 3 > /proc/sys/vm/drop_caches"
            )
            RootExecutor.runMultiple(discardCmds, logErrors = false)
        }

        // 5. Stage BCB recovery wipe
        stageBcbWipeMarker()

        // 6. Force hardware reboot
        executeHardwareReset()
        return@withContext true
    }

    private fun zeroFillMetadataKeyBlock(blockDevicePath: String): Boolean {
        return try {
            val file = RandomAccessFile(blockDevicePath, "rws")
            val zeroBytes = ByteArray(65536) // 64KB metadata header
            file.seek(0)
            file.write(zeroBytes)
            file.fd.sync()
            file.close()
            Log.i(TAG, "64KB metadata key block zeroed on: $blockDevicePath")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Direct RandomAccessFile write restricted: ${e.message}")
            false
        }
    }

    private fun stageBcbWipeMarker() {
        try {
            val cacheDir = File("/cache/recovery")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val cmdFile = File(cacheDir, "command")
            cmdFile.writeText("--wipe_data\n--reason=UncleTed_FBE_CryptoShred\n")
            cmdFile.setReadable(true, false)
        } catch (_: Exception) {}
    }

    private fun executeHardwareReset() {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "echo 1 > /proc/sys/kernel/sysrq && echo c > /proc/sysrq-trigger"))
        } catch (_: Exception) {
            try {
                Runtime.getRuntime().exec(arrayOf("reboot", "recovery"))
            } catch (_: Exception) {}
        }
    }
}