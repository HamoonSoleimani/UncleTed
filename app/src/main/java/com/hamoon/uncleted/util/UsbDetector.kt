package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log

object UsbDetector {
    private const val TAG = "UsbDetector"

    /**
     * Validates whether an active USB data host connection is present.
     * Replaces naive power-supply string matching with true Linux USB Device Controller (UDC)
     * gadget state inspection to prevent false-positive wipes on USB-PD chargers.
     */
    suspend fun isDataCableConnected(context: Context): Boolean {
        if (!RootChecker.isDeviceRooted()) return false

        try {
            // 1. Primary Check: Linux Kernel USB Device Controller (UDC) Gadget State
            // If connected to a PC or forensic tool, state switches to 'configured'.
            // Wall chargers and USB-PD docks leave this in 'not attached' or 'powered'.
            val udcResult = RootExecutor.run("cat /sys/class/udc/*/state 2>/dev/null")
            if (udcResult.isSuccess && udcResult.output.isNotEmpty()) {
                val state = udcResult.output.firstOrNull()?.trim()?.lowercase() ?: ""
                Log.d(TAG, "UDC gadget state: $state")
                if (state == "configured") {
                    Log.w(TAG, "USB endpoint fully CONFIGURED by active host controller.")
                    return true
                }
            }

            // 2. Secondary Check: Legacy ConfigFS Gadget State
            val legacyGadgetResult = RootExecutor.run("cat /sys/class/android_usb/android0/state 2>/dev/null")
            if (legacyGadgetResult.isSuccess && legacyGadgetResult.output.isNotEmpty()) {
                val state = legacyGadgetResult.output.firstOrNull()?.trim()?.uppercase() ?: ""
                if (state == "CONFIGURED") {
                    Log.w(TAG, "Legacy android_usb state is CONFIGURED.")
                    return true
                }
            }

            // 3. Tertiary Check: Explicit ADB Communication Service
            val adbState = RootExecutor.run("getprop sys.usb.state")
            if (adbState.isSuccess) {
                val state = adbState.output.firstOrNull() ?: ""
                if (state.contains("adb") || state.contains("mtp") || state.contains("ptp")) {
                    // Verify if current draws are from an SDP host, not a pure wall charger
                    val pwrTypeResult = RootExecutor.run("cat /sys/class/power_supply/usb/type 2>/dev/null")
                    val pwrType = pwrTypeResult.output.firstOrNull()?.trim()?.uppercase() ?: ""
                    if (pwrType == "SDP" || pwrType == "CDP") {
                        Log.w(TAG, "ADB/Data properties active on data-capable port: $pwrType")
                        return true
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating USB data connection state", e)
        }

        return false
    }
}