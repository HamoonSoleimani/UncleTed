package com.hamoon.uncleted.proximity

import android.content.Context
import android.util.Base64
import android.util.Log
import com.hamoon.uncleted.crypto.StrongBoxSecurityManager
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.NativeSecurityBridge
import java.security.SecureRandom

object ProximityShardingEngine {

    private const val TAG = "ProximitySharding"
    private const val DEFAULT_SECRET_LENGTH = 32 // 256-bit symmetric entropy

    @Volatile
    private var volatileShardB: ByteArray? = null
    private val lock = Any()

    /**
     * Generates a fresh 256-bit root operational secret, splits it into two Shamir 2-of-2
     * additive secret shares (S = Shard_A XOR Shard_B), seals Shard A inside discrete StrongBox HSM,
     * and returns Shard B (Base64) for transmission to the paired wearable token.
     */
    fun provisionFreshMasterShards(context: Context): String? {
        synchronized(lock) {
            val masterSecret = ByteArray(DEFAULT_SECRET_LENGTH)
            SecureRandom().nextBytes(masterSecret)

            val (shardA, shardB) = splitSecret(masterSecret)
            NativeSecurityBridge.zeroByteArray(masterSecret)

            val sealed = initializeAndStoreLocalShard(context, shardA)
            NativeSecurityBridge.zeroByteArray(shardA)

            return if (sealed) {
                val shardBBase64 = Base64.encodeToString(shardB, Base64.NO_WRAP)
                // Load local Shard B directly into volatile memory for immediate authorization
                loadVolatileShardB(context, shardB)
                NativeSecurityBridge.zeroByteArray(shardB)
                Log.i(TAG, "Master secret provisioned and split. Shard A sealed in HSM, Shard B active in RAM.")
                EventLogger.log(context, "PROXIMITY: Master secret split into 2-of-2 Shamir shares. Shard A sealed.")
                shardBBase64
            } else {
                NativeSecurityBridge.zeroByteArray(shardB)
                Log.e(TAG, "Failed sealing Shard A into hardware security module.")
                null
            }
        }
    }

    /**
     * Splits a master secret byte array into two 2-of-2 additive secret shares.
     * Information-theoretically secure: neither share alone reveals any bits of the master secret.
     */
    fun splitSecret(masterSecret: ByteArray): Pair<ByteArray, ByteArray> {
        val len = masterSecret.size
        val shardA = ByteArray(len)
        val shardB = ByteArray(len)

        SecureRandom().nextBytes(shardA)

        for (i in 0 until len) {
            shardB[i] = (masterSecret[i].toInt() xor shardA[i].toInt()).toByte()
        }

        NativeSecurityBridge.pinMemory(shardA)
        NativeSecurityBridge.pinMemory(shardB)

        return Pair(shardA, shardB)
    }

    /**
     * Reconstructs the master secret from local Shard A and volatile Shard B.
     */
    fun reconstructSecret(shardA: ByteArray, shardB: ByteArray): ByteArray? {
        if (shardA.size != shardB.size || shardA.isEmpty()) {
            Log.e(TAG, "Shard length mismatch or empty shards during reconstruction.")
            return null
        }

        val len = shardA.size
        val reconstructed = ByteArray(len)

        for (i in 0 until len) {
            reconstructed[i] = (shardA[i].toInt() xor shardB[i].toInt()).toByte()
        }

        NativeSecurityBridge.pinMemory(reconstructed)
        return reconstructed
    }

    fun initializeAndStoreLocalShard(context: Context, shardA: ByteArray): Boolean {
        synchronized(lock) {
            return try {
                val encrypted = StrongBoxSecurityManager.encryptWithStrongBox(context, shardA)
                if (encrypted == null) {
                    Log.e(TAG, "Failed sealing Shard A inside discrete StrongBox Keystore.")
                    return false
                }

                val (ctB64, ivB64) = encrypted.encodeToBase64()
                val serialized = "$ctB64:$ivB64"
                SecurityPreferences.setStoredShardA(context, serialized)
                Log.i(TAG, "Shard A sealed into StrongBox Keystore successfully.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error sealing Shard A: ${e.message}", e)
                false
            }
        }
    }

    fun loadVolatileShardB(context: Context, shardB: ByteArray) {
        synchronized(lock) {
            purgeVolatileShard(context)

            val clone = shardB.clone()
            NativeSecurityBridge.pinMemory(clone)
            volatileShardB = clone

            Log.i(TAG, "Ephemeral Shard B loaded into pinned volatile RAM.")
            EventLogger.log(context, "PROXIMITY: Ephemeral Shard B registered from trusted peripheral.")
        }
    }

    fun retrieveDecryptedShardA(context: Context): ByteArray? {
        val serialized = SecurityPreferences.getStoredShardA(context) ?: return null
        val parts = serialized.split(":")
        if (parts.size != 2) return null

        val ct = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)

        val payload = StrongBoxSecurityManager.StrongBoxPayload(ct, iv)
        return StrongBoxSecurityManager.decryptWithStrongBox(context, payload)
    }

    /**
     * Reconstructs the combined 256-bit symmetric operational key.
     * Returns null if Shard B has evaporated from RAM (e.g. operator walked away > 2m).
     */
    fun getActiveCombinedKey(context: Context): ByteArray? {
        synchronized(lock) {
            val shardB = volatileShardB
            if (shardB == null) {
                Log.w(TAG, "Shard B is not loaded in volatile RAM. Access denied.")
                return null
            }

            val shardA = retrieveDecryptedShardA(context)
            if (shardA == null) {
                Log.w(TAG, "Failed retrieving Shard A from StrongBox Keystore.")
                return null
            }

            val combined = reconstructSecret(shardA, shardB)

            NativeSecurityBridge.unpinMemory(shardA)
            NativeSecurityBridge.zeroByteArray(shardA)

            return combined
        }
    }

    fun isShardBActive(): Boolean {
        synchronized(lock) {
            return volatileShardB != null
        }
    }

    fun hasSealedShardA(context: Context): Boolean {
        return !SecurityPreferences.getStoredShardA(context).isNullOrEmpty()
    }

    fun purgeVolatileShard(context: Context) {
        synchronized(lock) {
            volatileShardB?.let {
                NativeSecurityBridge.unpinMemory(it)
                NativeSecurityBridge.zeroByteArray(it)
                volatileShardB = null
                Log.w(TAG, "Volatile Shard B purged and zeroed from RAM.")
                EventLogger.log(context, "PROXIMITY: Volatile Shard B sanitized via native memory barrier.")
            }
        }
    }
}