package com.hamoon.uncleted.sentinels

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.RootChecker
import com.hamoon.uncleted.util.RootExecutor
import kotlinx.coroutines.*
import java.io.File

object UsbTrapdoorController {

    private const val TAG = "UsbTrapdoorController"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var eventMonitorJob: Job? = null
    private var isTrapdoorArmed = false

    fun armTrapdoor(context: Context) {
        isTrapdoorArmed = true
        // Proactive, instantaneous data line severing
        scope.launch {
            severPhysicalDataLines(context)
            startHardwareInterruptMonitor(context)
        }
    }

    fun disarmTrapdoor(context: Context) {
        isTrapdoorArmed = false
        eventMonitorJob?.cancel()
        eventMonitorJob = null
        scope.launch {
            restorePhysicalDataLines(context)
        }
    }

    suspend fun severPhysicalDataLines(context: Context) = withContext(Dispatchers.IO) {
        Log.w(TAG, "Zero-Latency USB PHY data line severing engaged.")

        // 1. Android 12+ USB HAL v1.3 Physical Disconnect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                dpm?.setUsbDataSignalingEnabled(false)
                Log.i(TAG, "DPM setUsbDataSignalingEnabled(false) executed.")
            } catch (e: Exception) {
                Log.w(TAG, "DPM USB data signaling disable failed: ${e.message}")
            }
        }

        // 2. Hardware DWC3 / Linux UDC register unbind via root
        if (RootChecker.isDeviceRooted()) {
            val kernelSeverCmds = listOf(
                "setprop sys.usb.config none",
                "setprop sys.usb.state none",
                "echo '' > /config/usb_gadget/g1/UDC 2>/dev/null || true",
                "for udc in /sys/class/udc/*; do echo '' > \"\$udc/state\" 2>/dev/null || true; done",
                "for mode in /sys/devices/platform/soc/*.dwc3/mode; do echo 'none' > \"\$mode\" 2>/dev/null || true; done"
            )
            RootExecutor.runMultiple(kernelSeverCmds, logErrors = false)
            EventLogger.log(context, "HARDWARE: USB PHY data lines severed at register layer.")
        }
    }

    suspend fun restorePhysicalDataLines(context: Context) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Restoring USB physical data signaling lines.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                dpm?.setUsbDataSignalingEnabled(true)
            } catch (_: Exception) {}
        }

        if (RootChecker.isDeviceRooted()) {
            val kernelRestoreCmds = listOf(
                "setprop sys.usb.config mtp,adb",
                "setprop sys.usb.state mtp,adb",
                "getprop sys.usb.controller > /config/usb_gadget/g1/UDC 2>/dev/null || true"
            )
            RootExecutor.runMultiple(kernelRestoreCmds, logErrors = false)
        }
    }

    private fun startHardwareInterruptMonitor(context: Context) {
        eventMonitorJob?.cancel()
        eventMonitorJob = scope.launch {
            while (isActive && isTrapdoorArmed) {
                // High-priority monitor targeting active data host enumeration
                if (isHostDataEnumerationAttempted()) {
                    Log.e(TAG, "!!! INTRUSION DETECTED: USB DATA ENUMERATION ATTEMPTED WHILE LOCKED !!!")
                    EventLogger.log(context, "CRITICAL: USB data handshake attempted on severed port. Triggering SoC panic.")
                    triggerUnconditionalHardwarePanic()
                    break
                }
                delay(100L) // 100ms fast-poll loop
            }
        }
    }

    private fun isHostDataEnumerationAttempted(): Boolean {
        // Fast-path inspection of UDC controller state
        val udcDir = File("/sys/class/udc")
        if (udcDir.exists() && udcDir.isDirectory) {
            udcDir.listFiles()?.forEach { file ->
                val stateFile = File(file, "state")
                if (stateFile.exists()) {
                    try {
                        val state = stateFile.readText().trim().lowercase()
                        if (state == "configured" || state == "attached" || state == "addressed") {
                            return true
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Fast-path inspection of charger type negotiation
        val pwrType = File("/sys/class/power_supply/usb/type")
        if (pwrType.exists()) {
            try {
                val type = pwrType.readText().trim().uppercase()
                if (type == "SDP" || type == "CDP") {
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    fun triggerUnconditionalHardwarePanic() {
        Log.e(TAG, "Executing low-level SoC kernel panic via sysrq-trigger...")
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "echo 1 > /proc/sys/kernel/sysrq && echo c > /proc/sysrq-trigger"))
        } catch (_: Exception) {
            try {
                Runtime.getRuntime().exec(arrayOf("reboot", "-f"))
            } catch (_: Exception) {}
        }
    }
}