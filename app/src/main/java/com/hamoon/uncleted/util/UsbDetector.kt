package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log

object UsbDetector {
    private const val TAG = "UsbDetector"

    /**
     * Validates whether an active USB data host connection is present.
     * Evaluates Linux Kernel USB Device Controller (UDC) gadget state, ConfigFS controllers,
     * and hardware power-supply upstream port types to prevent false-positive triggers
     * on standard USB-PD wall chargers, docks, and wireless charging pads.
     */
    suspend fun isDataCableConnected(@Suppress("UNUSED_PARAMETER") context: Context): Boolean {
        if (!RootChecker.isDeviceRooted()) {
            return false
        }

        try {
            // 1. Primary Check: Linux Kernel USB Device Controller (UDC) Gadget State
            val udcResult = RootExecutor.run("cat /sys/class/udc/*/state 2>/dev/null", logErrors = false)
            if (udcResult.isSuccess && udcResult.output.isNotEmpty()) {
                for (line in udcResult.output) {
                    val state = line.trim().lowercase()
                    Log.d(TAG, "Kernel UDC gadget state: $state")
                    if (state == "configured") {
                        Log.w(TAG, "USB endpoint actively CONFIGURED by upstream data host controller.")
                        return true
                    }
                }
            }

            // 2. Secondary Check: Modern Linux ConfigFS UDC Binding
            val configFsResult = RootExecutor.run("cat /config/usb_gadget/g1/UDC 2>/dev/null", logErrors = false)
            if (configFsResult.isSuccess && configFsResult.output.isNotEmpty()) {
                val boundDriver = configFsResult.output.firstOrNull()?.trim() ?: ""
                if (boundDriver.isNotEmpty() && boundDriver != "none") {
                    val g1StateResult = RootExecutor.run("cat /config/usb_gadget/g1/state 2>/dev/null", logErrors = false)
                    val g1State = g1StateResult.output.firstOrNull()?.trim()?.lowercase() ?: ""
                    if (g1State == "configured") {
                        Log.w(TAG, "ConfigFS USB gadget controller reports CONFIGURED state.")
                        return true
                    }
                }
            }

            // 3. Tertiary Check: Legacy Android USB gadget driver state
            val legacyGadgetResult = RootExecutor.run("cat /sys/class/android_usb/android0/state 2>/dev/null", logErrors = false)
            if (legacyGadgetResult.isSuccess && legacyGadgetResult.output.isNotEmpty()) {
                val state = legacyGadgetResult.output.firstOrNull()?.trim()?.uppercase() ?: ""
                if (state == "CONFIGURED") {
                    Log.w(TAG, "Legacy android_usb state reports CONFIGURED.")
                    return true
                }
            }

            // 4. Quaternary Check: Power Supply Port Upstream Type
            val pwrTypeResult = RootExecutor.run("cat /sys/class/power_supply/usb/type /sys/class/power_supply/*/type 2>/dev/null", logErrors = false)
            if (pwrTypeResult.isSuccess && pwrTypeResult.output.isNotEmpty()) {
                for (line in pwrTypeResult.output) {
                    val pwrType = line.trim().uppercase()
                    if (pwrType == "SDP" || pwrType == "CDP") {
                        val adbState = RootExecutor.run("getprop sys.usb.state", logErrors = false).output.firstOrNull() ?: ""
                        if (adbState.contains("adb") || adbState.contains("mtp") || adbState.contains("ptp")) {
                            Log.w(TAG, "Active data port type ($pwrType) confirmed with active data protocol: $adbState")
                            return true
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting USB data bus connection state", e)
        }

        return false
    }
}