package com.hamoon.uncleted.util

import android.util.Log

object NativeSecurityBridge {

    private const val TAG = "NativeSecurityBridge"
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("uncleted_native")
            isLoaded = true
            Log.i(TAG, "libuncleted_native.so loaded successfully.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed loading libuncleted_native.so: ${t.message}", t)
            isLoaded = false
        }
    }

    fun isNativeLoaded(): Boolean = isLoaded

    external fun applyProcessHardening(): Boolean
    external fun isMteActive(): Boolean
    external fun secureZeroMemory(buffer: ByteArray)
    external fun purgeBlockDevice(blockDevicePath: String): Boolean
    external fun lockMemoryPages(data: ByteArray): Boolean
    external fun unlockMemoryPages(data: ByteArray): Boolean
    external fun executeCesNative(input: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray?
    external fun executeCesDecryptNative(inputWithTag: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray?

    fun enforceSecurityBaselines(): Boolean {
        if (!isLoaded) {
            Log.w(TAG, "Native library not loaded. Running in degraded userspace mode.")
            return false
        }
        val hardened = applyProcessHardening()
        val mte = isMteActive()
        Log.i(TAG, "Process Hardening Status: $hardened | Synchronous ARM MTE Active: $mte")
        return hardened
    }

    fun zeroByteArray(buffer: ByteArray?) {
        if (buffer == null || buffer.isEmpty()) return
        if (isLoaded) {
            secureZeroMemory(buffer)
        } else {
            buffer.fill(0)
        }
    }

    fun executeSiliconDiscard(blockDevicePath: String): Boolean {
        if (!isLoaded) {
            return false
        }
        return try {
            purgeBlockDevice(blockDevicePath)
        } catch (e: Exception) {
            Log.e(TAG, "Native purgeBlockDevice threw exception: ${e.message}")
            false
        }
    }

    fun pinMemory(data: ByteArray): Boolean {
        return if (isLoaded) lockMemoryPages(data) else false
    }

    fun unpinMemory(data: ByteArray): Boolean {
        return if (isLoaded) unlockMemoryPages(data) else false
    }

    fun compressAndEncrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray? {
        if (!isLoaded) return null
        return executeCesNative(plaintext, key, nonce)
    }

    fun decryptAndDecompress(ciphertextWithTag: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray? {
        if (!isLoaded) return null
        return executeCesDecryptNative(ciphertextWithTag, key, nonce)
    }
}