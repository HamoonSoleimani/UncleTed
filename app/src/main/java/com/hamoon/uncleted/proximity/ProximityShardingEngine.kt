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

    @Volatile
    private var volatileShardB: ByteArray? = null
    private val lock = Any()

    /**
     * Splits a master secret byte array into two 2-of-2 information-theoretically secure shards.
     * Shard A is returned for StrongBox hardware sealing.
     * Shard B is returned for transmission to the paired BLE/UWB peripheral.
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

    /**
     * Provisions Shard A into local StrongBox storage and prepares the engine.
     */
    fun initializeAndStoreLocalShard(context: Context, shardA: ByteArray): Boolean {
        return synchronized(lock) {
            try {
                val encrypted = StrongBoxSecurityManager.encryptWithStrongBox(context, shardA)
                if (encrypted == null) {
                    Log.e(TAG, "Failed sealing Shard A inside discrete StrongBox.")
                    return false
                }

                val (ctB64, ivB64) = encrypted.encodeToBase64()
                val serialized = "$ctB64:$ivB64"
                SecurityPreferences.setStoredShardA(context, serialized)
                Log.i(TAG, "Shard A sealed into StrongBox Keystore successfully.")
                EventLogger.log(context, "PROXIMITY: Shard A sealed inside discrete HSM.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error sealing Shard A", e)
                false
            } finally {
                NativeSecurityBridge.unpinMemory(shardA)
                NativeSecurityBridge.zeroByteArray(shardA)
            }
        }
    }

    /**
     * Loads and holds Shard B in volatile memory upon successful BLE handshake.
     */
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

    /**
     * Retrieves the decrypted local Shard A from StrongBox.
     */
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
     * Assembles the combined master key if Shard B is currently active in memory.
     */
    fun getActiveCombinedKey(context: Context): ByteArray? {
        synchronized(lock) {
            val shardB = volatileShardB ?: return null
            val shardA = retrieveDecryptedShardA(context) ?: return null

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

    /**
     * Overwrites Shard B in volatile RAM using native barriers upon proximity loss.
     */
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