package com.hamoon.uncleted.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.hamoon.uncleted.crypto.CryptoPreferences
import com.hamoon.uncleted.crypto.PostQuantumEngine
import com.hamoon.uncleted.crypto.StrongBoxSecurityManager
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.proximity.ProximityShardingEngine
import java.security.MessageDigest
import java.security.SecureRandom

object AdvancedCrypto {

    private const val TAG = "AdvancedCrypto"

    data class EncryptedData(
        val cipherText: String,
        val iv: String,
        val tag: String? = null
    )

    data class PostQuantumEnvelope(
        val wireEncapsulationBase64: String,
        val encryptedPayloadBase64: String,
        val nonceBase64: String
    )

    /**
     * Encrypts sensitive plaintexts.
     * If BLE Proximity Sharding is enabled, data is combined with the volatile Shamir Shard B.
     * If separated from the wearable token, encryption/decryption is cryptographically barred.
     */
    fun encryptSensitiveData(context: Context, plaintext: String): EncryptedData? {
        val plainBytes = plaintext.toByteArray(Charsets.UTF_8)
        NativeSecurityBridge.pinMemory(plainBytes)

        val isShardingEnabled = SecurityPreferences.isProximityShardingEnabled(context)
        val payloadBytes = if (isShardingEnabled) {
            val combinedKey = ProximityShardingEngine.getActiveCombinedKey(context)
            if (combinedKey == null) {
                Log.e(TAG, "Encryption rejected: Proximity token is separated (Shard B absent from RAM).")
                NativeSecurityBridge.unpinMemory(plainBytes)
                NativeSecurityBridge.zeroByteArray(plainBytes)
                return null
            }
            val nonce = ByteArray(12)
            SecureRandom().nextBytes(nonce)
            val encryptedWithShard = NativeSecurityBridge.compressAndEncrypt(plainBytes, combinedKey, nonce)
            NativeSecurityBridge.zeroByteArray(combinedKey)
            encryptedWithShard
        } else {
            plainBytes
        }

        if (payloadBytes == null) {
            NativeSecurityBridge.unpinMemory(plainBytes)
            NativeSecurityBridge.zeroByteArray(plainBytes)
            return null
        }

        val strongBoxPayload = StrongBoxSecurityManager.encryptWithStrongBox(context, payloadBytes)
        NativeSecurityBridge.unpinMemory(plainBytes)
        NativeSecurityBridge.zeroByteArray(plainBytes)
        if (isShardingEnabled) {
            NativeSecurityBridge.zeroByteArray(payloadBytes)
        }

        if (strongBoxPayload == null) {
            Log.e(TAG, "Failed encrypting data via StrongBox KeyMint.")
            return null
        }

        val (ctBase64, ivBase64) = strongBoxPayload.encodeToBase64()
        return EncryptedData(
            cipherText = ctBase64,
            iv = ivBase64
        )
    }

    /**
     * Decrypts sensitive ciphertexts with StrongBox and proximity key reconstruction.
     */
    fun decryptSensitiveData(context: Context, encryptedData: EncryptedData): String? {
        val cipherBytes = try {
            Base64.decode(encryptedData.cipherText, Base64.NO_WRAP)
        } catch (e: Exception) {
            return null
        }

        val ivBytes = try {
            Base64.decode(encryptedData.iv, Base64.NO_WRAP)
        } catch (e: Exception) {
            return null
        }

        val payload = StrongBoxSecurityManager.StrongBoxPayload(cipherBytes, ivBytes)
        val decryptedBytes = StrongBoxSecurityManager.decryptWithStrongBox(context, payload) ?: return null

        val isShardingEnabled = SecurityPreferences.isProximityShardingEnabled(context)
        val finalPlainBytes = if (isShardingEnabled) {
            val combinedKey = ProximityShardingEngine.getActiveCombinedKey(context)
            if (combinedKey == null) {
                Log.e(TAG, "Decryption rejected: Proximity token separated (Shard B absent from RAM).")
                NativeSecurityBridge.unpinMemory(decryptedBytes)
                NativeSecurityBridge.zeroByteArray(decryptedBytes)
                return null
            }
            // Extract nonce and ciphertext from shard payload
            val nonce = ByteArray(12)
            if (decryptedBytes.size < 12) {
                NativeSecurityBridge.zeroByteArray(combinedKey)
                return null
            }
            System.arraycopy(decryptedBytes, 0, nonce, 0, 12)
            val cipherOnly = ByteArray(decryptedBytes.size - 12)
            System.arraycopy(decryptedBytes, 12, cipherOnly, 0, cipherOnly.size)

            val plaintext = NativeSecurityBridge.decryptAndDecompress(cipherOnly, combinedKey, nonce)
            NativeSecurityBridge.zeroByteArray(combinedKey)
            plaintext
        } else {
            decryptedBytes
        }

        if (finalPlainBytes == null) {
            NativeSecurityBridge.unpinMemory(decryptedBytes)
            NativeSecurityBridge.zeroByteArray(decryptedBytes)
            return null
        }

        val resultString = String(finalPlainBytes, Charsets.UTF_8)
        NativeSecurityBridge.unpinMemory(finalPlainBytes)
        NativeSecurityBridge.zeroByteArray(finalPlainBytes)
        return resultString
    }

    /**
     * Seals payload with ML-KEM-768 + X25519 hybrid encapsulation followed by native CES pipeline.
     */
    fun sealWithPostQuantumHybrid(context: Context, plaintextData: ByteArray): PostQuantumEnvelope? {
        val trustedRemoteKeyBase64 = CryptoPreferences.getTrustedPqcPublicKey(context)
        if (trustedRemoteKeyBase64.isNullOrEmpty()) {
            Log.e(TAG, "PQC sealing aborted: No trusted remote hybrid public key configured.")
            return null
        }

        val recipientPublicKey = PostQuantumEngine.HybridPublicKey.decodeFromBase64(trustedRemoteKeyBase64)
            ?: return null

        val encapsulation = PostQuantumEngine.encapsulate(recipientPublicKey) ?: return null

        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)

        val ciphertextWithTag = NativeSecurityBridge.compressAndEncrypt(plaintextData, encapsulation.sharedKey256, nonce)
        NativeSecurityBridge.zeroByteArray(encapsulation.sharedKey256)

        if (ciphertextWithTag == null) {
            Log.e(TAG, "Native CES compression and encryption failed during PQC envelope sealing.")
            return null
        }

        return PostQuantumEnvelope(
            wireEncapsulationBase64 = Base64.encodeToString(encapsulation.wireCiphertext, Base64.NO_WRAP),
            encryptedPayloadBase64 = Base64.encodeToString(ciphertextWithTag, Base64.NO_WRAP),
            nonceBase64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
        )
    }

    /**
     * Opens post-quantum hybrid envelope with local private key.
     */
    fun openPostQuantumHybrid(context: Context, envelope: PostQuantumEnvelope): ByteArray? {
        val localPrivateKey = CryptoPreferences.getLocalPqcPrivateKey(context)
        if (localPrivateKey == null) {
            Log.e(TAG, "PQC envelope open aborted: Local private key missing from storage.")
            return null
        }

        val wireCiphertext = try {
            Base64.decode(envelope.wireEncapsulationBase64, Base64.NO_WRAP)
        } catch (e: Exception) { return null }

        val ciphertextWithTag = try {
            Base64.decode(envelope.encryptedPayloadBase64, Base64.NO_WRAP)
        } catch (e: Exception) { return null }

        val nonce = try {
            Base64.decode(envelope.nonceBase64, Base64.NO_WRAP)
        } catch (e: Exception) { return null }

        val sharedSecret256 = PostQuantumEngine.decapsulate(wireCiphertext, localPrivateKey) ?: return null

        val decrypted = NativeSecurityBridge.decryptAndDecompress(ciphertextWithTag, sharedSecret256, nonce)
        NativeSecurityBridge.zeroByteArray(sharedSecret256)

        return decrypted
    }

    fun executeCryptographicSuicide(context: Context): Boolean {
        return StrongBoxSecurityManager.executeMasterKeySuicide(context)
    }

    fun generateSecureToken(length: Int = 32): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val secureRandom = SecureRandom()
        return (1..length)
            .map { chars[secureRandom.nextInt(chars.length)] }
            .joinToString("")
    }

    fun hashWithSalt(data: String, salt: String? = null): String {
        val actualSalt = salt ?: generateSecureToken(16)
        val combined = data + actualSalt

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hash, Base64.NO_WRAP).trim() + ":$actualSalt"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hash data", e)
            ""
        }
    }

    fun verifyHash(data: String, hashedWithSalt: String): Boolean {
        return try {
            val parts = hashedWithSalt.split(":")
            if (parts.size != 2) return false

            val originalHash = parts[0]
            val salt = parts[1]
            val newHash = hashWithSalt(data, salt).split(":")[0]

            secureCompare(originalHash, newHash)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify hash", e)
            false
        }
    }

    fun secureCompare(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(aBytes, bBytes)
    }
}