package com.hamoon.uncleted.crypto

import android.content.Context
import android.os.Build
import android.util.Base64

object CryptoPreferences {

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

    // NIST FIPS 203 Hybrid Post-Quantum Keys (ML-KEM-768 + X25519)
    private const val KEY_PQC_HYBRID_ENABLED = "pqc_hybrid_enabled"
    private const val KEY_PQC_LOCAL_PUBLIC = "pqc_local_hybrid_public_key"
    private const val KEY_PQC_LOCAL_PRIVATE_X = "pqc_local_private_x25519"
    private const val KEY_PQC_LOCAL_PRIVATE_K = "pqc_local_private_kyber768"
    private const val KEY_PQC_TRUSTED_REMOTE_PUB = "pqc_trusted_remote_hybrid_public_key"

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
        return prefs.getBoolean(KEY_CLEARTEXT_SMS_ALLOWED, true)
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
        return prefs.getLong(KEY_HARDWARE_MONOTONIC_COUNTER, 0L)
    }

    fun setHardwareMonotonicCounter(context: Context, counter: Long) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_HARDWARE_MONOTONIC_COUNTER, counter).commit()
    }

    // =========================================================================
    // 3. NIST FIPS 203 Post-Quantum Hybrid Cryptography (ML-KEM-768 + X25519)
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
        prefs.edit()
            .putString(KEY_PQC_LOCAL_PUBLIC, keyPair.public.encodeToBase64())
            .putString(KEY_PQC_LOCAL_PRIVATE_X, Base64.encodeToString(keyPair.private.x25519Private, Base64.NO_WRAP))
            .putString(KEY_PQC_LOCAL_PRIVATE_K, Base64.encodeToString(keyPair.private.kyberPrivate, Base64.NO_WRAP))
            .commit()
    }

    fun getLocalPqcPublicKey(context: Context): PostQuantumEngine.HybridPublicKey? {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pubB64 = prefs.getString(KEY_PQC_LOCAL_PUBLIC, null) ?: return null
        return PostQuantumEngine.HybridPublicKey.decodeFromBase64(pubB64)
    }

    fun getLocalPqcPrivateKey(context: Context): PostQuantumEngine.HybridPrivateKey? {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val xPrivB64 = prefs.getString(KEY_PQC_LOCAL_PRIVATE_X, null) ?: return null
        val kPrivB64 = prefs.getString(KEY_PQC_LOCAL_PRIVATE_K, null) ?: return null

        return try {
            val xPriv = Base64.decode(xPrivB64, Base64.NO_WRAP)
            val kPriv = Base64.decode(kPrivB64, Base64.NO_WRAP)
            PostQuantumEngine.HybridPrivateKey(xPriv, kPriv)
        } catch (_: Exception) {
            null
        }
    }

    fun clearLocalPqcKeyPair(context: Context) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PQC_LOCAL_PUBLIC)
            .remove(KEY_PQC_LOCAL_PRIVATE_X)
            .remove(KEY_PQC_LOCAL_PRIVATE_K)
            .commit()
    }
}