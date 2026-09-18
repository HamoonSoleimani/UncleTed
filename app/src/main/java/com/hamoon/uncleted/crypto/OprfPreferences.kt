package com.hamoon.uncleted.crypto

import android.content.Context
import android.os.Build
import android.content.SharedPreferences

object OprfPreferences {

    private const val PREFS_NAME = "oprf_hardened_store"
    private const val KEY_OPRF_ENABLED = "oprf_key_decoupling_enabled"
    private const val KEY_OPRF_SERVER_URL = "oprf_server_endpoint_url"
    private const val KEY_OPRF_SERVER_PUBKEY = "oprf_server_public_key_hex"
    private const val KEY_OPRF_LOCAL_KEY_SHARE = "oprf_local_key_share_hex"
    private const val KEY_OPRF_TIMEOUT_MS = "oprf_network_timeout_ms"

    private fun getStorageContext(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isOprfEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_OPRF_ENABLED, false)

    fun setOprfEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_OPRF_ENABLED, enabled).commit()

    fun getServerUrl(context: Context): String =
        getPrefs(context).getString(KEY_OPRF_SERVER_URL, "https://oprf.uncleted.internal/evaluate") ?: "https://oprf.uncleted.internal/evaluate"

    fun setServerUrl(context: Context, url: String) =
        getPrefs(context).edit().putString(KEY_OPRF_SERVER_URL, url.trim()).commit()

    fun getServerPublicKey(context: Context): String? =
        getPrefs(context).getString(KEY_OPRF_SERVER_PUBKEY, null)

    fun setServerPublicKey(context: Context, pubKeyHex: String?) =
        getPrefs(context).edit().putString(KEY_OPRF_SERVER_PUBKEY, pubKeyHex?.trim()).commit()

    fun getLocalKeyShare(context: Context): String? =
        getPrefs(context).getString(KEY_OPRF_LOCAL_KEY_SHARE, null)

    fun setLocalKeyShare(context: Context, shareHex: String?) =
        getPrefs(context).edit().putString(KEY_OPRF_LOCAL_KEY_SHARE, shareHex?.trim()).commit()

    fun getTimeoutMs(context: Context): Long =
        getPrefs(context).getLong(KEY_OPRF_TIMEOUT_MS, 4000L)

    fun setTimeoutMs(context: Context, timeoutMs: Long) =
        getPrefs(context).edit().putLong(KEY_OPRF_TIMEOUT_MS, timeoutMs).commit()
}