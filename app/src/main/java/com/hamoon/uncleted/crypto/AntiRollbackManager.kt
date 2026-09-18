package com.hamoon.uncleted.crypto

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom

object AntiRollbackManager {

    private const val TAG = "AntiRollbackManager"
    private const val RPMB_STATE_FILE = "rpmb_rollback_anchor.bin"

    // Allow up to 3 checkpoints of asynchronous flush tolerance (e.g. abrupt power-cut or OS crash)
    private const val MAX_ALLOWABLE_BACKWARD_DRIFT = 3L
    // If the counter rewinds by more than 50 checkpoints, it represents a restored NAND snapshot
    private const val ATTACK_SUSPICION_THRESHOLD = 50L

    private val lock = Any()

    @Synchronized
    fun verifyStateIntegrity(context: Context): Boolean {
        synchronized(lock) {
            val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }

            val lastPersistedHardwareSeq = CryptoPreferences.getHardwareMonotonicCounter(deContext)
            val currentLocalSeq = readInternalStateCounter(deContext)

            Log.d(TAG, "Anti-rollback evaluation: Persisted=$lastPersistedHardwareSeq, LocalFile=$currentLocalSeq")

            // Check for severe backward rewind indicative of flash restoration / snapshotting
            if (currentLocalSeq + MAX_ALLOWABLE_BACKWARD_DRIFT < lastPersistedHardwareSeq) {
                val delta = lastPersistedHardwareSeq - currentLocalSeq
                Log.e(TAG, "SECURITY ALERT: Flash state rewind detected! Delta: $delta (Local: $currentLocalSeq, Persisted: $lastPersistedHardwareSeq)")
                EventLogger.log(context, "SECURITY WARNING: Monotonic counter desynchronization detected (Delta: $delta).")

                if (delta >= ATTACK_SUSPICION_THRESHOLD) {
                    // Enter non-destructive BFU lock rather than irreversible instant bricking
                    triggerDefensiveLockdown(context, "SEVERE_NAND_REWIND_DELTA_$delta")
                    return false
                }
            }

            // Monotonically advance both anchors atomically
            val nextSeq = maxOf(lastPersistedHardwareSeq, currentLocalSeq) + 1L
            CryptoPreferences.setHardwareMonotonicCounter(deContext, nextSeq)
            writeInternalStateCounter(deContext, nextSeq)

            return true
        }
    }

    @Synchronized
    fun registerSecurityEventAdvance(context: Context) {
        synchronized(lock) {
            val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }
            val current = maxOf(
                CryptoPreferences.getHardwareMonotonicCounter(deContext),
                readInternalStateCounter(deContext)
            )
            val updated = current + 1L
            CryptoPreferences.setHardwareMonotonicCounter(deContext, updated)
            writeInternalStateCounter(deContext, updated)
            Log.d(TAG, "Hardware anti-rollback monotonic sequence advanced to: $updated")
        }
    }

    private fun readInternalStateCounter(context: Context): Long {
        val file = File(context.filesDir, RPMB_STATE_FILE)
        if (!file.exists()) {
            val initial = 1000L + SecureRandom().nextInt(500)
            writeInternalStateCounter(context, initial)
            return initial
        }

        val atomicFile = AtomicFile(file)
        return try {
            atomicFile.openRead().use { input ->
                val bytes = ByteArray(8)
                val read = input.read(bytes)
                if (read == 8) {
                    var v = 0L
                    for (i in 0 until 8) {
                        v = (v shl 8) or (bytes[i].toLong() and 0xFF)
                    }
                    v
                } else {
                    0L
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading atomic state counter file: ${e.message}")
            0L
        }
    }

    private fun writeInternalStateCounter(context: Context, value: Long) {
        val file = File(context.filesDir, RPMB_STATE_FILE)
        val atomicFile = AtomicFile(file)
        var fos: FileOutputStream? = null

        try {
            fos = atomicFile.startWrite()
            val bytes = ByteArray(8)
            var temp = value
            for (i in 7 downTo 0) {
                bytes[i] = (temp and 0xFF).toByte()
                temp = temp shr 8
            }
            fos.write(bytes)
            fos.flush()
            atomicFile.finishWrite(fos)
        } catch (e: Exception) {
            Log.e(TAG, "Error atomically writing state counter: ${e.message}", e)
            if (fos != null) {
                atomicFile.failWrite(fos)
            }
        }
    }

    private fun triggerDefensiveLockdown(context: Context, reason: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.e(TAG, "Engaging defensive lockdown (BFU key eviction & lock) due to: $reason")
                EventLogger.log(context, "ANTI-ROLLBACK: Defensive lockdown engaged. Device locked into cold BFU state.")
                val strategy = DefenseCoordinator.resolveStrategy(context)
                strategy.evictMemoryKeysAndLock()
            } catch (e: Exception) {
                Log.e(TAG, "Failed executing defensive rollback lockdown", e)
            }
        }
    }
}