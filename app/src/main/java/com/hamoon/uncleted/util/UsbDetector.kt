package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log

object UsbDetector {
    private const val TAG = "UsbDetector"

    private val DEDICATED_CHARGER_TYPES = setOf(
        "DCP",
        "USB_DCP",
        "FLOAT",
        "USB_FLOAT",
        "HVDCP",
        "USB_HVDCP",
        "USB_HVDCP_3",
        "WIRELESS",
        "WIPOWER"
    )

    /**
     * Validates whether an active USB data host connection is present.
     * Evaluates Linux Kernel USB Device Controller (UDC) gadget state, ConfigFS controllers,
     * and hardware power-supply upstream port types while filtering out transient electrical
     * fluctuations from USB-PD power delivery and fast chargers.
     */
    suspend fun isDataCableConnected(@Suppress("UNUSED_PARAMETER") context: Context): Boolean {
        if (!RootChecker.isDeviceRooted()) {
            return false
        }

        try {
            // Step 1: Check upstream power supply port type to immediately filter out pure wall adapters
            val pwrTypeResult = RootExecutor.run("cat /sys/class/power_supply/usb/type /sys/class/power_supply/*/type 2>/dev/null", logErrors = false)
            if (pwrTypeResult.isSuccess && pwrTypeResult.output.isNotEmpty()) {
                val detectedTypes = pwrTypeResult.output.map { it.trim().uppercase() }

                val isOnlyDedicatedCharger = detectedTypes.isNotEmpty() && detectedTypes.all { type ->
                    DEDICATED_CHARGER_TYPES.contains(type)
                }

                if (isOnlyDedicatedCharger) {
                    Log.d(TAG, "Suppressed transient: USB port negotiated as dedicated charger (${detectedTypes.joinToString()}).")
                    return false
                }
            }

            // Step 2: Primary Check - Linux Kernel USB Device Controller (UDC) Gadget State
            val udcResult = RootExecutor.run("cat /sys/class/udc/*/state 2>/dev/null", logErrors = false)
            if (udcResult.isSuccess && udcResult.output.isNotEmpty()) {
                for (line in udcResult.output) {
                    val state = line.trim().lowercase()
                    Log.d(TAG, "Kernel UDC gadget state: $state")
                    if (state == "configured" || state == "attached" || state == "addressed") {
                        Log.w(TAG, "USB endpoint actively in '$state' state by upstream data host controller.")
                        return true
                    }
                }
            }

            // Step 3: Secondary Check - Modern Linux ConfigFS UDC Binding
            val configFsResult = RootExecutor.run("cat /config/usb_gadget/g1/UDC 2>/dev/null", logErrors = false)
            if (configFsResult.isSuccess && configFsResult.output.isNotEmpty()) {
                val boundDriver = configFsResult.output.firstOrNull()?.trim() ?: ""
                if (boundDriver.isNotEmpty() && boundDriver != "none") {
                    val g1StateResult = RootExecutor.run("cat /config/usb_gadget/g1/state 2>/dev/null", logErrors = false)
                    val g1State = g1StateResult.output.firstOrNull()?.trim()?.lowercase() ?: ""
                    if (g1State == "configured" || g1State == "attached") {
                        Log.w(TAG, "ConfigFS USB gadget controller reports '$g1State' state.")
                        return true
                    }
                }
            }

            // Step 4: Tertiary Check - Legacy Android USB gadget driver state
            val legacyGadgetResult = RootExecutor.run("cat /sys/class/android_usb/android0/state 2>/dev/null", logErrors = false)
            if (legacyGadgetResult.isSuccess && legacyGadgetResult.output.isNotEmpty()) {
                val state = legacyGadgetResult.output.firstOrNull()?.trim()?.uppercase() ?: ""
                if (state == "CONFIGURED") {
                    Log.w(TAG, "Legacy android_usb state reports CONFIGURED.")
                    return true
                }
            }

            // Step 5: Quaternary Check - Power Supply Port Upstream Type (SDP / CDP host connection)
            if (pwrTypeResult.isSuccess && pwrTypeResult.output.isNotEmpty()) {
                for (line in pwrTypeResult.output) {
                    val pwrType = line.trim().uppercase()
                    if (pwrType == "SDP" || pwrType == "CDP") {
                        val adbState = RootExecutor.run("getprop sys.usb.state", logErrors = false).output.firstOrNull() ?: ""
                        if (adbState.contains("adb") || adbState.contains("mtp") || adbState.contains("ptp")) {
                            Log.w(TAG, "Active data port type ($pwrType) confirmed with data protocol: $adbState")
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