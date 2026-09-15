package com.hamoon.uncleted.crypto

import android.content.Context
import android.os.Build

object CryptoPreferences {

    private const val PREFS_NAME = "hardened_crypto_store"
    private const val KEY_TRUSTED_PUBKEY = "trusted_ed25519_pubkey"
    private const val KEY_LAST_SEQUENCE = "last_wire_sequence"
    private const val KEY_CLEARTEXT_SMS_ALLOWED = "allow_cleartext_sms"
    private const val KEY_STRONGBOX_ENFORCED = "strongbox_enforced"
    private const val KEY_SUICIDE_EXECUTED = "cryptographic_suicide_executed"
    private const val KEY_HARDWARE_ATTESTED = "hardware_attestation_verified"

    private fun getStorageContext(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

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

    fun isStrongBoxEnforced(context: Context): Boolean {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_STRONGBOX_ENFORCED, false)
    }

    fun setStrongBoxEnforced(context: Context, enforced: Boolean) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_STRONGBOX_ENFORCED, enforced).commit()
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
}