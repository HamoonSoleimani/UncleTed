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

    fun enforceSecurityBaselines(): Boolean {
        if (!isLoaded) {
            Log.w(TAG, "Native library not loaded. Security baselines running in degraded mode.")
            return false
        }
        val hardened = applyProcessHardening()
        val mte = isMteActive()
        Log.i(TAG, "Process Hardening Status: $hardened | Synchronous MTE Hardware Active: $mte")
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
}