package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    suspend fun isDataCableConnected(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!RootChecker.isDeviceRooted()) {
            return@withContext false
        }

        try {
            val udcDir = File("/sys/class/udc")
            if (udcDir.exists() && udcDir.canRead()) {
                val stateFiles = udcDir.listFiles()
                if (stateFiles != null) {
                    for (file in stateFiles) {
                        val stateFile = File(file, "state")
                        if (stateFile.exists() && stateFile.canRead()) {
                            val state = stateFile.readText().trim().lowercase()
                            if (state == "configured" || state == "attached" || state == "addressed") {
                                return@withContext true
                            }
                        }
                    }
                }
            }

            val combinedScript = "pwr=\$(cat /sys/class/power_supply/usb/type /sys/class/power_supply/*/type 2>/dev/null | tr '\\n' ' ') ; " +
                    "udc=\$(cat /sys/class/udc/*/state /config/usb_gadget/g1/state 2>/dev/null | tr '\\n' ' ') ; " +
                    "echo \"PWR:\$pwr|UDC:\$udc\""

            val result = RootExecutor.run(combinedScript, logErrors = false)
            if (result.isSuccess && result.output.isNotEmpty()) {
                val line = result.output.first()
                val parts = line.split("|")
                val pwrTypes = parts.getOrNull(0)?.substringAfter("PWR:")?.trim()?.uppercase() ?: ""
                val udcStates = parts.getOrNull(1)?.substringAfter("UDC:")?.trim()?.lowercase() ?: ""

                val isDedicatedCharger = pwrTypes.isNotEmpty() && pwrTypes.split(" ").filter { it.isNotBlank() }.all {
                    DEDICATED_CHARGER_TYPES.contains(it)
                }
                if (isDedicatedCharger) {
                    return@withContext false
                }

                if (udcStates.contains("configured") || udcStates.contains("attached") || udcStates.contains("addressed")) {
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting USB data bus connection state", e)
        }

        return@withContext false
    }
}