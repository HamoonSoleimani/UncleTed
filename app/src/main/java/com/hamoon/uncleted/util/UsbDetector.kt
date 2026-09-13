package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log

object UsbDetector {
    private const val TAG = "UsbDetector"

    // Common paths in Android Kernels for USB state.
    // Manufacturers (Samsung, Xiaomi, Pixel) often use slightly different paths.
    // A robust app checks multiple known locations.
    private val USB_TYPE_PATHS = listOf(
        "/sys/class/power_supply/usb/type",
        "/sys/class/power_supply/main/type",
        "/sys/class/udc/*/state" // Advanced: Checks USB Device Controller state
    )

    /**
     * Checks the Kernel SysFS to see if a DATA connection is established.
     * Returns TRUE if a data-capable connection (SDP, CDP) is detected.
     * Returns FALSE if it's just a wall charger (DCP) or disconnected.
     */
    suspend fun isDataCableConnected(context: Context): Boolean {
        if (!RootChecker.isDeviceRooted()) return false

        try {
            // Check 1: Standard Power Supply Type
            USB_TYPE_PATHS.forEach { path ->
                // "ls" to check if path exists first is safer, but "cat" with error suppression works
                val result = RootExecutor.run("cat $path")
                if (result.isSuccess && result.output.isNotEmpty()) {
                    val type = result.output.first().trim()
                    Log.d(TAG, "Kernel USB Type at $path: $type")

                    // Known Data Types: SDP (PC), CDP (High current PC), USB, USB_PD_DRP
                    // Known Safe Types: DCP (Wall Charger), Unknown (often wireless)
                    if (type.contains("SDP") || type.contains("CDP") || type.equals("USB")) {
                        return true
                    }
                }
            }

            // Check 2: Check ADB State (Forensic tools often trigger adb daemon)
            val adbState = RootExecutor.run("getprop sys.usb.state")
            if (adbState.isSuccess && adbState.output.firstOrNull()?.contains("adb") == true) {
                Log.w(TAG, "ADB State detected active via Props")
                return true
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking USB state via Root", e)
        }
        return false
    }
}