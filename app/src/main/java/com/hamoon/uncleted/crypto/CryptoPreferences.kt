package com.hamoon.uncleted.crypto

import android.content.Context
import android.os.Build

object CryptoPreferences {

    private const val PREFS_NAME = "hardened_crypto_store"
    private const val KEY_TRUSTED_PUBKEY = "trusted_ed25519_pubkey"
    private const val KEY_LAST_SEQUENCE = "last_wire_sequence"
    private const val KEY_CLEARTEXT_SMS_ALLOWED = "allow_cleartext_sms"

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
        return prefs.getBoolean(KEY_CLEARTEXT_SMS_ALLOWED, true) // Default true for graceful fallback
    }

    fun setCleartextSmsAllowed(context: Context, allowed: Boolean) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CLEARTEXT_SMS_ALLOWED, allowed).commit()
    }
}