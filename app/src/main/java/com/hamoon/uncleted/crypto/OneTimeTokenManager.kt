package com.hamoon.uncleted.crypto

import android.content.Context
import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object OneTimeTokenManager {

    private const val PREFS_NAME = "uncleted_otc_store"
    private const val KEY_ACTIVE_TOKEN_HASHES = "active_otc_hashes"
    private const val TOKEN_COUNT = 5

    private fun getStorageContext(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    /**
     * Generates a batch of 5 high-entropy single-use emergency wipe tokens.
     * Hashes of the tokens are stored in Device-Protected storage (accessible in BFU).
     * The plaintext strings are returned to the caller for printing/saving.
     */
    fun generateNewTokenBatch(context: Context): List<String> {
        val secureRandom = SecureRandom()
        val plainTokens = mutableListOf<String>()
        val tokenHashes = mutableSetOf<String>()

        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        for (i in 1..TOKEN_COUNT) {
            val sb = StringBuilder("!UT:OTC-W$i-")
            for (j in 0 until 12) {
                if (j > 0 && j % 4 == 0) sb.append("-")
                sb.append(alphabet[secureRandom.nextInt(alphabet.length)])
            }
            val tokenString = sb.toString()
            plainTokens.add(tokenString)
            tokenHashes.add(computeSha256(tokenString))
        }

        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_ACTIVE_TOKEN_HASHES, tokenHashes).commit()

        return plainTokens
    }

    fun getRemainingTokenCount(context: Context): Int {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_ACTIVE_TOKEN_HASHES, emptySet())?.size ?: 0
    }

    /**
     * Validates and immediately burns an incoming OTC token in DE storage.
     */
    fun validateAndBurnToken(context: Context, rawMessageBody: String): Boolean {
        val normalizedToken = rawMessageBody.trim()
        if (!normalizedToken.startsWith("!UT:OTC-")) {
            return false
        }

        val incomingHash = computeSha256(normalizedToken)
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeHashes = prefs.getStringSet(KEY_ACTIVE_TOKEN_HASHES, emptySet())?.toMutableSet() ?: return false

        if (activeHashes.contains(incomingHash)) {
            activeHashes.remove(incomingHash)
            prefs.edit().putStringSet(KEY_ACTIVE_TOKEN_HASHES, activeHashes).commit()
            return true
        }

        return false
    }

    fun clearAllTokens(context: Context) {
        val prefs = getStorageContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ACTIVE_TOKEN_HASHES).commit()
    }

    private fun computeSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }
}