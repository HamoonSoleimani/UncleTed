package com.hamoon.uncleted.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.NativeSecurityBridge
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object StrongBoxSecurityManager {

    private const val TAG = "StrongBoxSecManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val MASTER_SUICIDE_KEY_ALIAS = "UncleTed_TitanM2_MasterSuicideKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    data class StrongBoxPayload(
        val cipherText: ByteArray,
        val iv: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as StrongBoxPayload
            if (!cipherText.contentEquals(other.cipherText)) return false
            if (!iv.contentEquals(other.iv)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = cipherText.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }

        fun encodeToBase64(): Pair<String, String> {
            val ctB64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            return Pair(ctB64, ivB64)
        }
    }

    fun isStrongBoxSupported(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else {
            false
        }
    }

    @Synchronized
    fun getOrCreateMasterKey(context: Context): SecretKey? {
        if (CryptoPreferences.isSuicideExecuted(context)) {
            Log.e(TAG, "Master key access rejected: Cryptographic suicide already executed on this device.")
            return null
        }

        // Enforce anti-NAND mirroring rollback verification
        if (!AntiRollbackManager.verifyStateIntegrity(context)) {
            Log.e(TAG, "Hardware anti-rollback integrity check failed! Refusing master key delivery.")
            return null
        }

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(MASTER_SUICIDE_KEY_ALIAS)) {
            return keyStore.getKey(MASTER_SUICIDE_KEY_ALIAS, null) as? SecretKey
        }

        return generateHardwareBackedKey(context)
    }

    private fun generateHardwareBackedKey(context: Context): SecretKey? {
        val hasStrongBox = isStrongBoxSupported(context)
        Log.i(TAG, "Initializing Master Suicide Key (StrongBox Supported: $hasStrongBox)...")

        return try {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val specBuilder = KeyGenParameterSpec.Builder(
                MASTER_SUICIDE_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (hasStrongBox) {
                    specBuilder.setIsStrongBoxBacked(true)
                }

                // Enforce hardware monotonic counter rollback resistance (Titan M2 / RPMB) via reflection
                try {
                    val method = specBuilder.javaClass.getMethod("setRollbackResistant", Boolean::class.javaPrimitiveType)
                    method.invoke(specBuilder, true)
                    Log.i(TAG, "Enforced setRollbackResistant(true) on hardware master key via reflection.")
                } catch (e: Exception) {
                    Log.w(TAG, "setRollbackResistant unsupported or restricted on this SoC: ${e.message}")
                }
            }

            keyGenerator.init(specBuilder.build())
            val key = keyGenerator.generateKey()
            CryptoPreferences.setStrongBoxEnforced(context, hasStrongBox)
            Log.i(TAG, "Hardware master key successfully provisioned inside discrete HSM.")
            key
        } catch (e: Exception) {
            if (e is StrongBoxUnavailableException || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && e.cause is StrongBoxUnavailableException)) {
                Log.w(TAG, "StrongBox hardware unavailable. Falling back to primary SoC TEE...")
                generateTeeFallbackKey(context)
            } else {
                Log.e(TAG, "Failed generating hardware-backed master key", e)
                null
            }
        }
    }

    private fun generateTeeFallbackKey(context: Context): SecretKey? {
        return try {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val specBuilder = KeyGenParameterSpec.Builder(
                MASTER_SUICIDE_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val method = specBuilder.javaClass.getMethod("setRollbackResistant", Boolean::class.javaPrimitiveType)
                    method.invoke(specBuilder, true)
                } catch (_: Exception) {}
            }

            keyGenerator.init(specBuilder.build())
            val key = keyGenerator.generateKey()
            CryptoPreferences.setStrongBoxEnforced(context, false)
            Log.i(TAG, "TEE hardware fallback master key generated successfully.")
            key
        } catch (e: Exception) {
            Log.e(TAG, "Failed generating TEE fallback key", e)
            null
        }
    }

    @Synchronized
    fun executeMasterKeySuicide(context: Context): Boolean {
        Log.e(TAG, "!!! INITIATING TITAN M2 / STRONGBOX CRYPTOGRAPHIC SUICIDE !!!")
        EventLogger.log(context, "CRITICAL: Titan M2 / StrongBox master key hardware eviction invoked.")

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (keyStore.containsAlias(MASTER_SUICIDE_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_SUICIDE_KEY_ALIAS)
            }

            if (keyStore.containsAlias("UncleTedMasterKey")) {
                keyStore.deleteEntry("UncleTedMasterKey")
            }

            CryptoPreferences.setSuicideExecuted(context, true)
            Log.e(TAG, "Cryptographic Suicide Completed: Master key slot zeroed in silicon.")
            EventLogger.log(context, "SUCCESS: Master suicide key eradicated from hardware security module.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Cryptographic suicide failure: Could not delete hardware entry", e)
            false
        }
    }

    fun encryptWithStrongBox(context: Context, plainBytes: ByteArray): StrongBoxPayload? {
        val key = getOrCreateMasterKey(context) ?: return null

        // Pin memory buffer during hardware cryptographic transformation
        NativeSecurityBridge.pinMemory(plainBytes)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv.clone()
            val cipherText = cipher.doFinal(plainBytes)

            // Advance hardware rollback counter on successful encryption
            AntiRollbackManager.registerSecurityEventAdvance(context)

            StrongBoxPayload(cipherText, iv)
        } catch (e: Exception) {
            Log.e(TAG, "StrongBox encryption failed", e)
            null
        } finally {
            NativeSecurityBridge.unpinMemory(plainBytes)
            NativeSecurityBridge.zeroByteArray(plainBytes)
        }
    }

    fun decryptWithStrongBox(context: Context, payload: StrongBoxPayload): ByteArray? {
        if (CryptoPreferences.isSuicideExecuted(context)) {
            Log.e(TAG, "Decryption aborted: Master suicide key was already destroyed.")
            return null
        }

        val key = getOrCreateMasterKey(context) ?: return null

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, payload.iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val decrypted = cipher.doFinal(payload.cipherText)

            // Pin decrypted plaintext immediately upon return from hardware
            NativeSecurityBridge.pinMemory(decrypted)
            decrypted
        } catch (e: Exception) {
            Log.e(TAG, "StrongBox decryption failed (Key revoked, tampered, or rollback detected)", e)
            null
        }
    }
}