package com.hamoon.uncleted.sentinels

import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import com.hamoon.uncleted.crypto.StrongBoxSecurityManager
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.EmergencyDestructionEngine
import com.hamoon.uncleted.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class PmicTamperSentinel(private val context: Context) {

    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    @Volatile
    private var baselineResistance: Long = -1L
    @Volatile
    private var baselineTemp: Long = -1L
    @Volatile
    private var isCalibrated = false
    @Volatile
    private var nodesAvailable = true

    companion object {
        private const val TAG = "PmicTamperSentinel"

        private const val MIN_PLAUSIBLE_BATTERY_TEMP = 50L  // 5.0°C (0.1°C units)
        private const val MAX_PLAUSIBLE_BATTERY_TEMP = 650L // 65.0°C (0.1°C units)

        private val RESISTANCE_CANDIDATE_PATHS = listOf(
            "/sys/class/power_supply/bms/resistance",
            "/sys/class/power_supply/battery/resistance_id",
            "/sys/class/power_supply/battery/resistance",
            "/sys/class/power_supply/maxfg/resistance"
        )

        private val TEMP_CANDIDATE_PATHS = listOf(
            "/sys/class/power_supply/battery/temp",
            "/sys/class/power_supply/bms/temp",
            "/sys/class/power_supply/maxfg/temp"
        )
    }

    fun getLiveTelemetry(): Pair<Long, Long> {
        if (!nodesAvailable) return Pair(-1L, -1L)
        val r = readFirstAvailableNode(RESISTANCE_CANDIDATE_PATHS)
        val t = readFirstAvailableNode(TEMP_CANDIDATE_PATHS)
        return Pair(r, t)
    }

    fun recalibrateBaseline(): Boolean {
        nodesAvailable = true
        val currentResistance = readFirstAvailableNode(RESISTANCE_CANDIDATE_PATHS)
        val currentTemp = readFirstAvailableNode(TEMP_CANDIDATE_PATHS)

        if (currentResistance <= 0L && currentTemp <= 0L) {
            Log.w(TAG, "SysFS BMS telemetry nodes unreadable. Sentinel disabled on this device.")
            nodesAvailable = false
            return false
        }

        baselineResistance = currentResistance
        baselineTemp = currentTemp
        isCalibrated = true
        Log.i(TAG, "Hardware PMIC baseline locked: R_int=${baselineResistance}uOhm, Temp=${baselineTemp}")
        EventLogger.log(context, "PMIC SENTINEL: Baseline recalibrated (R_int=${baselineResistance}uOhm, Temp=${baselineTemp}).")
        return true
    }

    fun inspectHardwareTelemetry() {
        if (!SecurityPreferences.isPmicTamperEnabled(context) || !nodesAvailable) {
            isCalibrated = false
            return
        }

        val isLocked = try {
            keyguardManager?.isDeviceLocked ?: false
        } catch (_: Exception) {
            false
        }
        if (!isLocked) {
            isCalibrated = false
            return
        }

        val currentResistance = readFirstAvailableNode(RESISTANCE_CANDIDATE_PATHS)
        val currentTemp = readFirstAvailableNode(TEMP_CANDIDATE_PATHS)

        if (currentResistance <= 0L && currentTemp <= 0L) {
            nodesAvailable = false
            return
        }

        if (!isCalibrated) {
            baselineResistance = currentResistance
            baselineTemp = currentTemp
            isCalibrated = true
            Log.i(TAG, "PMIC telemetry baseline locked: R_int=${baselineResistance}uOhm, Temp=${baselineTemp}")
            return
        }

        val maxAllowedResistanceDelta = SecurityPreferences.getPmicImpedanceDeltaThreshold(context)
        val maxAllowedThermalShockDelta = SecurityPreferences.getPmicThermalShockDelta(context)

        // Vector 1: Battery Terminal Splicing / Benchtop DC Jig Attachment
        if (baselineResistance > 0L && currentResistance > 0L) {
            val resistanceDelta = Math.abs(currentResistance - baselineResistance)
            if (resistanceDelta >= maxAllowedResistanceDelta) {
                Log.e(TAG, "TAMPER BREACH: Impedance step-jump: ${resistanceDelta}uOhm (Limit: ${maxAllowedResistanceDelta}uOhm)")
                EventLogger.log(context, "FATAL: Battery terminal impedance jump (${resistanceDelta}uOhm). Intrusion detected.")
                triggerHardwareSanitize("BATTERY_SPLICING_DC_JIG_DETECTED")
                return
            }
        }

        // Vector 2: Chassis Unsealing / Thermal Dissipation Shock
        if (currentTemp > 0L) {
            if (currentTemp < MIN_PLAUSIBLE_BATTERY_TEMP || currentTemp > MAX_PLAUSIBLE_BATTERY_TEMP) {
                Log.e(TAG, "TAMPER BREACH: Out-of-bounds thermal reading ($currentTemp).")
                EventLogger.log(context, "FATAL: Out-of-bounds battery thermal reading ($currentTemp).")
                triggerHardwareSanitize("CHASSIS_OPEN_THERMAL_ANOMALY")
                return
            }

            if (baselineTemp > 0L) {
                val tempDrop = baselineTemp - currentTemp
                if (tempDrop >= maxAllowedThermalShockDelta) {
                    Log.e(TAG, "TAMPER BREACH: Rapid thermal gradient drop: ${tempDrop / 10.0}°C (Limit: ${maxAllowedThermalShockDelta / 10.0}°C)")
                    EventLogger.log(context, "FATAL: Thermal gradient shock drop detected (${tempDrop / 10.0}°C).")
                    triggerHardwareSanitize("THERMAL_ENCLOSURE_DISASSEMBLY")
                    return
                }
            }
        }

        // Running moving average
        if (currentResistance > 0L) {
            baselineResistance = (baselineResistance * 7 + currentResistance) / 8
        }
        if (currentTemp > 0L) {
            baselineTemp = (baselineTemp * 7 + currentTemp) / 8
        }
    }

    private fun readFirstAvailableNode(candidatePaths: List<String>): Long {
        for (path in candidatePaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val raw = file.readText().trim()
                    val value = raw.toLongOrNull()
                    if (value != null && value > 0L) {
                        return value
                    }
                }
            } catch (_: Exception) {}
        }
        return -1L
    }

    private fun triggerHardwareSanitize(reason: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.e(TAG, "!!! INITIATING IMMEDIATE SILICON SUICIDE: $reason !!!")
                StrongBoxSecurityManager.executeMasterKeySuicide(context)
                EmergencyDestructionEngine.evictAndZeroEncryptionKeys()
                EmergencyDestructionEngine.executeKernelRebootFallback()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing PMIC sanitize sequence", e)
            }
        }
    }
}
