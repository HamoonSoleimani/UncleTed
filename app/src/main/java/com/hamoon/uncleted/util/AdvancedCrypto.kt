package com.hamoon.uncleted.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.hamoon.uncleted.crypto.CryptoPreferences
import com.hamoon.uncleted.crypto.PostQuantumEngine
import com.hamoon.uncleted.crypto.StrongBoxSecurityManager
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

    fun encryptSensitiveData(context: Context, plaintext: String): EncryptedData? {
        val plainBytes = plaintext.toByteArray(Charsets.UTF_8)
        val payload = StrongBoxSecurityManager.encryptWithStrongBox(context, plainBytes)
        NativeSecurityBridge.zeroByteArray(plainBytes)

        if (payload == null) {
            Log.e(TAG, "Failed to encrypt data via StrongBox KeyMint.")
            return null
        }

        val (ctBase64, ivBase64) = payload.encodeToBase64()
        return EncryptedData(
            cipherText = ctBase64,
            iv = ivBase64
        )
    }

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

        val resultString = String(decryptedBytes, Charsets.UTF_8)
        NativeSecurityBridge.unpinMemory(decryptedBytes)
        NativeSecurityBridge.zeroByteArray(decryptedBytes)
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

        // Pass through native Compress-Encrypt-Shape (CES) engine
        val ciphertextWithTag = NativeSecurityBridge.compressAndEncrypt(plaintextData, encapsulation.sharedKey256, nonce)

        // Zero ephemeral symmetric key immediately
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
            Log.e(TAG, "PQC envelope open aborted: Local private key missing from Device-Protected store.")
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