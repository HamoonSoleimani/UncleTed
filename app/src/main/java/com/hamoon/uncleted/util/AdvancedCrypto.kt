package com.hamoon.uncleted.util

import android.content.Context
import android.util.Base64
import android.util.Log
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

    /**
     * Encrypts plaintext using discrete StrongBox KeyMint with volatile memory clearing.
     */
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

    /**
     * Decrypts ciphertext using discrete StrongBox KeyMint.
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

        val resultString = String(decryptedBytes, Charsets.UTF_8)
        NativeSecurityBridge.zeroByteArray(decryptedBytes)
        return resultString
    }

    /**
     * Triggers instantaneous hardware silicon key erasure.
     */
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

    fun generateSecurePin(length: Int = 6): String {
        val secureRandom = SecureRandom()
        return (1..length)
            .map { secureRandom.nextInt(10) }
            .joinToString("")
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    fun secureCompare(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(aBytes, bBytes)
    }

    fun hideDataInNoise(context: Context, sensitiveData: String): ByteArray {
        val encrypted = encryptSensitiveData(context, sensitiveData) ?: return byteArrayOf()
        val dataToHide = "${encrypted.cipherText}|${encrypted.iv}".toByteArray(Charsets.UTF_8)
        val noiseSize = 1024 + dataToHide.size * 8
        val noise = ByteArray(noiseSize)
        SecureRandom().nextBytes(noise)

        for (i in dataToHide.indices) {
            val byte = dataToHide[i]
            for (bit in 0..7) {
                val bitValue = (byte.toInt() shr bit) and 1
                val noiseIndex = i * 8 + bit
                if (noiseIndex < noise.size) {
                    noise[noiseIndex] = (noise[noiseIndex].toInt() and 0xFE or bitValue).toByte()
                }
            }
        }
        return noise
    }

    fun extractDataFromNoise(context: Context, noiseData: ByteArray, dataLength: Int): String? {
        return try {
            val extractedBytes = ByteArray(dataLength)

            for (i in extractedBytes.indices) {
                var byte = 0
                for (bit in 0..7) {
                    val noiseIndex = i * 8 + bit
                    if (noiseIndex < noiseData.size) {
                        val bitValue = noiseData[noiseIndex].toInt() and 1
                        byte = byte or (bitValue shl bit)
                    }
                }
                extractedBytes[i] = byte.toByte()
            }

            val dataString = String(extractedBytes, Charsets.UTF_8)
            NativeSecurityBridge.zeroByteArray(extractedBytes)

            val parts = dataString.split("|")
            if (parts.size == 2) {
                val encryptedData = EncryptedData(parts[0], parts[1])
                decryptSensitiveData(context, encryptedData)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract data from noise", e)
            null
        }
    }
}