package com.hamoon.uncleted.crypto

import android.content.Context
import android.os.Build
import android.util.Log
import com.hamoon.uncleted.util.EmergencyDestructionEngine
import com.hamoon.uncleted.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.SecureRandom

object AntiRollbackManager {

    private const val TAG = "AntiRollbackManager"
    private const val RPMB_STATE_FILE = "rpmb_rollback_anchor.bin"
    private const val MAX_ALLOWABLE_BACKWARD_DRIFT = 0L

    @Synchronized
    fun verifyStateIntegrity(context: Context): Boolean {
        val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }

        val lastPersistedHardwareSeq = CryptoPreferences.getHardwareMonotonicCounter(deContext)
        val currentLocalSeq = readInternalStateCounter(deContext)

        Log.d(TAG, "Evaluating hardware anti-rollback counters: Persisted=$lastPersistedHardwareSeq, LocalState=$currentLocalSeq")

        if (currentLocalSeq < lastPersistedHardwareSeq - MAX_ALLOWABLE_BACKWARD_DRIFT) {
            Log.e(TAG, "CRITICAL: Flash rollback detected! Counter rewind: Local=$currentLocalSeq < Persisted=$lastPersistedHardwareSeq")
            EventLogger.log(context, "FATAL: Flash rollback / NAND Mirroring desynchronization detected!")
            triggerRollbackDefenseTrap(context, "NAND_MIRRORING_DESYNC_LOCAL_$currentLocalSeq")
            return false
        }

        // Advance hardware monotonic checkpoint
        val nextSeq = maxOf(lastPersistedHardwareSeq, currentLocalSeq) + 1L
        CryptoPreferences.setHardwareMonotonicCounter(deContext, nextSeq)
        writeInternalStateCounter(deContext, nextSeq)

        return true
    }

    fun registerSecurityEventAdvance(context: Context) {
        val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
        val current = CryptoPreferences.getHardwareMonotonicCounter(deContext)
        val updated = current + 1L
        CryptoPreferences.setHardwareMonotonicCounter(deContext, updated)
        writeInternalStateCounter(deContext, updated)
        Log.d(TAG, "Hardware anti-rollback monotonic sequence advanced to $updated")
    }

    private fun readInternalStateCounter(context: Context): Long {
        return try {
            val file = context.getFileStreamPath(RPMB_STATE_FILE)
            if (!file.exists()) {
                val initial = 1000L + SecureRandom().nextInt(500)
                writeInternalStateCounter(context, initial)
                return initial
            }
            context.openFileInput(RPMB_STATE_FILE).use { input ->
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
            Log.w(TAG, "Error reading RPMB state anchor file", e)
            0L
        }
    }

    private fun writeInternalStateCounter(context: Context, value: Long) {
        try {
            val bytes = ByteArray(8)
            var temp = value
            for (i in 7 downTo 0) {
                bytes[i] = (temp and 0xFF).toByte()
                temp = temp shr 8
            }
            context.openFileOutput(RPMB_STATE_FILE, Context.MODE_PRIVATE).use { output ->
                output.write(bytes)
                output.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing RPMB state anchor file", e)
        }
    }

    private fun triggerRollbackDefenseTrap(context: Context, reason: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.e(TAG, "!!! TRIGGERING HARDWARE SUICIDE AGAINST NAND MIRRORING ATTACK: $reason !!!")
                StrongBoxSecurityManager.executeMasterKeySuicide(context)
                EmergencyDestructionEngine.evictAndZeroEncryptionKeys()
                EmergencyDestructionEngine.executeKernelRebootFallback()
            } catch (e: Exception) {
                Log.e(TAG, "Failure during rollback trap execution", e)
            }
        }
    }
}