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

    private var baselineResistance: Long = -1L
    private var baselineTemp: Long = -1L
    private var isCalibrated = false

    companion object {
        private const val TAG = "PmicTamperSentinel"

        // Step change: > 35 mΩ (35,000 µΩ) shift indicates benchtop DC power supply micro-clamp splice
        private const val RESISTANCE_DELTA_THRESHOLD_UOHM = 35_000L

        // Temperature thresholds (tenths of a degree Celsius: 150 = 15.0°C, 600 = 60.0°C)
        private const val MIN_PLAUSIBLE_BATTERY_TEMP = 50L  // 5.0°C
        private const val MAX_PLAUSIBLE_BATTERY_TEMP = 650L // 65.0°C
        private const val THERMAL_GRADIENT_SHOCK_DELTA = 120L // Rapid 12.0°C drop from chassis opening

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

    fun inspectHardwareTelemetry() {
        if (!SecurityPreferences.isPmicTamperEnabled(context)) {
            isCalibrated = false
            return
        }

        // Only enforce anti-disassembly tripwires when screen is locked
        val isLocked = keyguardManager?.isDeviceLocked ?: true
        if (!isLocked) {
            isCalibrated = false
            return
        }

        val currentResistance = readFirstAvailableNode(RESISTANCE_CANDIDATE_PATHS)
        val currentTemp = readFirstAvailableNode(TEMP_CANDIDATE_PATHS)

        if (currentResistance <= 0L && currentTemp <= 0L) {
            // Hardware platform does not expose BMS telemetry nodes
            return
        }

        if (!isCalibrated) {
            baselineResistance = currentResistance
            baselineTemp = currentTemp
            isCalibrated = true
            Log.i(TAG, "Hardware PMIC telemetry baseline locked: R_int=${baselineResistance}uOhm, Temp=${baselineTemp}")
            return
        }

        // Vector 1: Battery Connector Splicing / External Power Supply Jig Insertion
        if (baselineResistance > 0L && currentResistance > 0L) {
            val resistanceDelta = Math.abs(currentResistance - baselineResistance)
            if (resistanceDelta >= RESISTANCE_DELTA_THRESHOLD_UOHM) {
                Log.e(TAG, "TAMPER BREACH: Impedance step-jump of ${resistanceDelta}uOhm detected! Bench power supply clamp attached.")
                EventLogger.log(context, "FATAL: Battery terminal impedance jump (${resistanceDelta}uOhm). Power jig intrusion.")
                triggerHardwareSanitize("BATTERY_SPLICING_DC_JIG_DETECTED")
                return
            }
        }

        // Vector 2: Chassis Opening / Rear Glass Removal Thermal Dissipation Shock
        if (currentTemp > 0L) {
            if (currentTemp < MIN_PLAUSIBLE_BATTERY_TEMP || currentTemp > MAX_PLAUSIBLE_BATTERY_TEMP) {
                Log.e(TAG, "TAMPER BREACH: Unnatural thermal reading (${currentTemp}). Chassis thermistor fault.")
                EventLogger.log(context, "FATAL: Out-of-bounds battery thermal reading ($currentTemp). Hardware breach.")
                triggerHardwareSanitize("CHASSIS_OPEN_THERMAL_ANOMALY")
                return
            }

            if (baselineTemp > 0L) {
                val tempDrop = baselineTemp - currentTemp
                if (tempDrop >= THERMAL_GRADIENT_SHOCK_DELTA) {
                    Log.e(TAG, "TAMPER BREACH: Rapid thermal gradient shock drop (${tempDrop / 10.0}°C). Rear enclosure unsealed.")
                    EventLogger.log(context, "FATAL: Thermal gradient shock drop detected. Enclosure breach.")
                    triggerHardwareSanitize("THERMAL_ENCLOSURE_DISASSEMBLY")
                    return
                }
            }
        }

        // Smooth moving average calibration
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