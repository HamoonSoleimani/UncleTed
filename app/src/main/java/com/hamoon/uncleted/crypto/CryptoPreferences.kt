package com.hamoon.uncleted.crypto

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import com.hamoon.uncleted.util.NativeSecurityBridge
import java.security.SecureRandom

object CryptoPreferences {

    private const val TAG = "CryptoPreferences"
    private const val PREFS_NAME = "hardened_crypto_store"

    // Asymmetric Remote Signaling & Replay Defense
    private const val KEY_TRUSTED_PUBKEY = "trusted_ed25519_pubkey"
    private const val KEY_LAST_SEQUENCE = "last_wire_sequence"
    private const val KEY_CLEARTEXT_SMS_ALLOWED = "allow_cleartext_sms"
    private const val KEY_WIRE_DRIFT_WINDOW_MS = "wire_drift_window_ms"

    // Hardware Keystore & Silicon Suicide
    private const val KEY_STRONGBOX_ENFORCED = "strongbox_enforced"
    private const val KEY_SUICIDE_EXECUTED = "cryptographic_suicide_executed"
    private const val KEY_HARDWARE_ATTESTED = "hardware_attestation_verified"
    private const val KEY_ROLLBACK_RESISTANT_ENFORCED = "rollback_resistant_enforced"

    // Hardware Monotonic Counter & Anti-Rollback (RPMB Anchor)
    private const val KEY_HARDWARE_MONOTONIC_COUNTER = "hardware_rpmb_monotonic_counter"

    // NIST FIPS 203 Hybrid Post-Quantum Keys (Encrypted at rest)
    private const val KEY_PQC_HYBRID_ENABLED = "pqc_hybrid_enabled"
    private const val KEY_PQC_LOCAL_PUBLIC = "pqc_local_hybrid_public_key"
    private const val KEY_PQC_LOCAL_PRIVATE_X_ENC = "pqc_enc_private_x25519"
    private const val KEY_PQC_LOCAL_PRIVATE_X_IV = "pqc_iv_private_x25519"
    private const val KEY_PQC_LOCAL_PRIVATE_K_ENC = "pqc_enc_private_kyber768"
    private const val KEY_PQC_LOCAL_PRIVATE_K_IV = "pqc_iv_private_kyber768"
    private const val KEY_PQC_TRUSTED_REMOTE_PUB = "pqc_trusted_remote_hybrid_public_key"

    // Plausible Deniability Master Vault Seed (Encrypted with StrongBox KeyStore)
    private const val KEY_VAULT_MASTER_SEED_ENC = "vault_master_seed_enc"
    private const val KEY_VAULT_MASTER_SEED_IV = "vault_master_seed_iv"

    private fun getStorageContext(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    // =========================================================================
    // 1. Ed25519 Remote Signaling & Replay Defense
    // =========================================================================
    fun getTrustedPublicKey(context: Context): String? {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TRUSTED_PUBKEY, null)
    }

    fun setTrustedPublicKey(context: Context, pubKeyBase64: String?) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TRUSTED_PUBKEY, pubKeyBase64?.trim()).commit()
    }

    fun getLastRecordedSequence(context: Context): Long {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_SEQUENCE, 0L)
    }

    fun setLastRecordedSequence(context: Context, sequence: Long) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_SEQUENCE, sequence).commit()
    }

    fun isCleartextSmsAllowed(context: Context): Boolean {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CLEARTEXT_SMS_ALLOWED, false) // Default false for security
    }

    fun setCleartextSmsAllowed(context: Context, allowed: Boolean) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CLEARTEXT_SMS_ALLOWED, allowed).commit()
    }

    fun getWireDriftWindowMs(context: Context): Long {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_WIRE_DRIFT_WINDOW_MS, 120_000L)
    }

    fun setWireDriftWindowMs(context: Context, windowMs: Long) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_WIRE_DRIFT_WINDOW_MS, windowMs).commit()
    }

    // =========================================================================
    // 2. Hardware Keystore, StrongBox & Anti-Rollback Monotonic Counters
    // =========================================================================
    fun isStrongBoxEnforced(context: Context): Boolean {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_STRONGBOX_ENFORCED, false)
    }

    fun setStrongBoxEnforced(context: Context, enforced: Boolean) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_STRONGBOX_ENFORCED, enforced).commit()
    }

    fun isRollbackResistantEnforced(context: Context): Boolean {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ROLLBACK_RESISTANT_ENFORCED, true)
    }

    fun setRollbackResistantEnforced(context: Context, enforced: Boolean) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ROLLBACK_RESISTANT_ENFORCED, enforced).commit()
    }

    fun isSuicideExecuted(context: Context): Boolean {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SUICIDE_EXECUTED, false)
    }

    fun setSuicideExecuted(context: Context, executed: Boolean) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SUICIDE_EXECUTED, executed).commit()
    }

    fun resetSuicideState(context: Context) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SUICIDE_EXECUTED).commit()
    }

    fun isHardwareAttestationVerified(context: Context): Boolean {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HARDWARE_ATTESTED, false)
    }

    fun setHardwareAttestationVerified(context: Context, verified: Boolean) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HARDWARE_ATTESTED, verified).commit()
    }

    fun getHardwareMonotonicCounter(context: Context): Long {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_HARDWARE_MONOTONIC_COUNTER, 1000L)
    }

    fun setHardwareMonotonicCounter(context: Context, counter: Long) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_HARDWARE_MONOTONIC_COUNTER, counter).commit()
    }

    // =========================================================================
    // 3. Plausible Deniability Master Vault Seed (Hardware Protected)
    // =========================================================================
    @Synchronized
    fun getOrGenerateVaultMasterSeed(context: Context): ByteArray? {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encB64 = prefs.getString(KEY_VAULT_MASTER_SEED_ENC, null)
        val ivB64 = prefs.getString(KEY_VAULT_MASTER_SEED_IV, null)

        if (encB64 != null && ivB64 != null) {
            return try {
                val encBytes = Base64.decode(encB64, Base64.NO_WRAP)
                val ivBytes = Base64.decode(ivB64, Base64.NO_WRAP)
                val payload = StrongBoxSecurityManager.StrongBoxPayload(encBytes, ivBytes)
                StrongBoxSecurityManager.decryptWithStrongBox(context, payload)
            } catch (e: Exception) {
                Log.e(TAG, "Failed decrypting vault master seed with hardware Keystore", e)
                null
            }
        }

        // Generate fresh 32-byte seed and seal inside StrongBox
        val newSeed = ByteArray(32)
        SecureRandom().nextBytes(newSeed)
        NativeSecurityBridge.pinMemory(newSeed)

        return try {
            val payload = StrongBoxSecurityManager.encryptWithStrongBox(context, newSeed)
            if (payload != null) {
                val (ct, iv) = payload.encodeToBase64()
                prefs.edit()
                    .putString(KEY_VAULT_MASTER_SEED_ENC, ct)
                    .putString(KEY_VAULT_MASTER_SEED_IV, iv)
                    .commit()
                newSeed.clone()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed encrypting fresh vault master seed", e)
            null
        } finally {
            NativeSecurityBridge.unpinMemory(newSeed)
            NativeSecurityBridge.zeroByteArray(newSeed)
        }
    }

    // =========================================================================
    // 4. NIST FIPS 203 Hybrid Post-Quantum Keys (Encrypted at Rest)
    // =========================================================================
    fun isPqcHybridEnabled(context: Context): Boolean {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PQC_HYBRID_ENABLED, true)
    }

    fun setPqcHybridEnabled(context: Context, enabled: Boolean) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PQC_HYBRID_ENABLED, enabled).commit()
    }

    fun getTrustedPqcPublicKey(context: Context): String? {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PQC_TRUSTED_REMOTE_PUB, null)
    }

    fun setTrustedPqcPublicKey(context: Context, hybridPubKeyBase64: String?) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PQC_TRUSTED_REMOTE_PUB, hybridPubKeyBase64?.trim()).commit()
    }

    fun saveLocalPqcKeyPair(context: Context, keyPair: PostQuantumEngine.HybridKeyPair) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Hardware encrypt the classical Curve25519 and lattice Kyber private keys
        val encX = StrongBoxSecurityManager.encryptWithStrongBox(context, keyPair.private.x25519Private)
        val encK = StrongBoxSecurityManager.encryptWithStrongBox(context, keyPair.private.kyberPrivate)

        if (encX != null && encK != null) {
            val (xCt, xIv) = encX.encodeToBase64()
            val (kCt, kIv) = encK.encodeToBase64()

            prefs.edit()
                .putString(KEY_PQC_LOCAL_PUBLIC, keyPair.public.encodeToBase64())
                .putString(KEY_PQC_LOCAL_PRIVATE_X_ENC, xCt)
                .putString(KEY_PQC_LOCAL_PRIVATE_X_IV, xIv)
                .putString(KEY_PQC_LOCAL_PRIVATE_K_ENC, kCt)
                .putString(KEY_PQC_LOCAL_PRIVATE_K_IV, kIv)
                .commit()
            Log.i(TAG, "PQC hybrid keypair sealed into hardware-encrypted storage.")
        } else {
            Log.e(TAG, "Failed sealing PQC private keys inside hardware Keystore!")
        }
    }

    fun getLocalPqcPublicKey(context: Context): PostQuantumEngine.HybridPublicKey? {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pubB64 = prefs.getString(KEY_PQC_LOCAL_PUBLIC, null) ?: return null
        return PostQuantumEngine.HybridPublicKey.decodeFromBase64(pubB64)
    }

    fun getLocalPqcPrivateKey(context: Context): PostQuantumEngine.HybridPrivateKey? {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val xCtB64 = prefs.getString(KEY_PQC_LOCAL_PRIVATE_X_ENC, null) ?: return null
        val xIvB64 = prefs.getString(KEY_PQC_LOCAL_PRIVATE_X_IV, null) ?: return null
        val kCtB64 = prefs.getString(KEY_PQC_LOCAL_PRIVATE_K_ENC, null) ?: return null
        val kIvB64 = prefs.getString(KEY_PQC_LOCAL_PRIVATE_K_IV, null) ?: return null

        return try {
            val xPayload = StrongBoxSecurityManager.StrongBoxPayload(
                Base64.decode(xCtB64, Base64.NO_WRAP),
                Base64.decode(xIvB64, Base64.NO_WRAP)
            )
            val kPayload = StrongBoxSecurityManager.StrongBoxPayload(
                Base64.decode(kCtB64, Base64.NO_WRAP),
                Base64.decode(kIvB64, Base64.NO_WRAP)
            )

            val xPriv = StrongBoxSecurityManager.decryptWithStrongBox(context, xPayload) ?: return null
            val kPriv = StrongBoxSecurityManager.decryptWithStrongBox(context, kPayload) ?: return null

            PostQuantumEngine.HybridPrivateKey(xPriv, kPriv)
        } catch (e: Exception) {
            Log.e(TAG, "Failed decrypting local PQC private keys", e)
            null
        }
    }

    fun clearLocalPqcKeyPair(context: Context) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PQC_LOCAL_PUBLIC)
            .remove(KEY_PQC_LOCAL_PRIVATE_X_ENC)
            .remove(KEY_PQC_LOCAL_PRIVATE_X_IV)
            .remove(KEY_PQC_LOCAL_PRIVATE_K_ENC)
            .remove(KEY_PQC_LOCAL_PRIVATE_K_IV)
            .commit()
    }
}