package com.hamoon.uncleted.sentinels

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.hamoon.uncleted.crypto.StrongBoxSecurityManager
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.EmergencyDestructionEngine
import com.hamoon.uncleted.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PmicTamperSentinel(private val context: Context) {

    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    @Volatile
    private var baselineResistance: Long = -1L
    @Volatile
    private var baselineTemp: Long = -1L
    @Volatile
    private var isCalibrated = false

    companion object {
        private const val TAG = "PmicTamperSentinel"
        private const val PREFS_NAME = "pmic_tamper_state"
        private const val KEY_NODES_SUPPORTED = "sysfs_nodes_supported"

        private const val MIN_PLAUSIBLE_BATTERY_TEMP = 50L
        private const val MAX_PLAUSIBLE_BATTERY_TEMP = 650L

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

        @Volatile
        private var cachedSupportStatus: Boolean? = null
    }

    private fun getDePrefs() = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.createDeviceProtectedStorageContext()
    } else {
        context
    }).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSupported(): Boolean {
        cachedSupportStatus?.let { return it }
        val prefs = getDePrefs()
        if (prefs.contains(KEY_NODES_SUPPORTED)) {
            val supported = prefs.getBoolean(KEY_NODES_SUPPORTED, false)
            cachedSupportStatus = supported
            return supported
        }
        return false
    }

    suspend fun probeSupportAsync(): Boolean = withContext(Dispatchers.IO) {
        cachedSupportStatus?.let { return@withContext it }
        val prefs = getDePrefs()
        if (prefs.contains(KEY_NODES_SUPPORTED)) {
            val supported = prefs.getBoolean(KEY_NODES_SUPPORTED, false)
            cachedSupportStatus = supported
            return@withContext supported
        }

        val r = readFirstAvailableNode(RESISTANCE_CANDIDATE_PATHS)
        val t = readFirstAvailableNode(TEMP_CANDIDATE_PATHS)
        val supported = r > 0L || t > 0L
        prefs.edit().putBoolean(KEY_NODES_SUPPORTED, supported).apply()
        cachedSupportStatus = supported
        if (!supported) {
            Log.w(TAG, "SysFS BMS telemetry nodes unreadable or denied by SELinux. Latching supported=false.")
        }
        supported
    }

    suspend fun getLiveTelemetry(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        if (!isSupported()) return@withContext Pair(-1L, -1L)
        val r = readFirstAvailableNode(RESISTANCE_CANDIDATE_PATHS)
        val t = readFirstAvailableNode(TEMP_CANDIDATE_PATHS)

        if (r <= 0L && t <= 0L) {
            Log.w(TAG, "Battery SysFS telemetry unreadable or blocked by SELinux. Latching supported=false.")
            cachedSupportStatus = false
            getDePrefs().edit().putBoolean(KEY_NODES_SUPPORTED, false).apply()
        }
        Pair(r, t)
    }

    suspend fun recalibrateBaseline(forceProbe: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!forceProbe && cachedSupportStatus == false) {
            Log.w(TAG, "PMIC baseline recalibration skipped: SysFS nodes are blocked by platform SELinux.")
            return@withContext false
        }

        val currentResistance = readFirstAvailableNode(RESISTANCE_CANDIDATE_PATHS)
        val currentTemp = readFirstAvailableNode(TEMP_CANDIDATE_PATHS)

        if (currentResistance <= 0L && currentTemp <= 0L) {
            Log.w(TAG, "SysFS BMS telemetry nodes unreadable or blocked by SELinux. Sentinel unsupported.")
            cachedSupportStatus = false
            getDePrefs().edit().putBoolean(KEY_NODES_SUPPORTED, false).apply()
            isCalibrated = false
            return@withContext false
        }

        cachedSupportStatus = true
        getDePrefs().edit().putBoolean(KEY_NODES_SUPPORTED, true).apply()
        baselineResistance = currentResistance
        baselineTemp = currentTemp
        isCalibrated = true
        Log.i(TAG, "Hardware PMIC baseline locked: R_int=${baselineResistance}uOhm, Temp=${baselineTemp}")
        EventLogger.log(context, "PMIC SENTINEL: Baseline recalibrated (R_int=${baselineResistance}uOhm, Temp=${baselineTemp}).")
        true
    }

    suspend fun inspectHardwareTelemetry() = withContext(Dispatchers.IO) {
        if (!SecurityPreferences.isPmicTamperEnabled(context) || !isSupported()) {
            isCalibrated = false
            return@withContext
        }

        val isLocked = try {
            keyguardManager?.isDeviceLocked ?: false
        } catch (_: Exception) {
            false
        }
        if (!isLocked) {
            isCalibrated = false
            return@withContext
        }

        val currentResistance = readFirstAvailableNode(RESISTANCE_CANDIDATE_PATHS)
        val currentTemp = readFirstAvailableNode(TEMP_CANDIDATE_PATHS)

        if (currentResistance <= 0L && currentTemp <= 0L) {
            Log.w(TAG, "SysFS nodes inaccessible during background lock inspection. Disabling PMIC sentinel.")
            cachedSupportStatus = false
            getDePrefs().edit().putBoolean(KEY_NODES_SUPPORTED, false).apply()
            isCalibrated = false
            return@withContext
        }

        if (!isCalibrated) {
            baselineResistance = currentResistance
            baselineTemp = currentTemp
            isCalibrated = true
            Log.i(TAG, "PMIC telemetry baseline locked: R_int=${baselineResistance}uOhm, Temp=${baselineTemp}")
            return@withContext
        }

        val maxAllowedResistanceDelta = SecurityPreferences.getPmicImpedanceDeltaThreshold(context)
        val maxAllowedThermalShockDelta = SecurityPreferences.getPmicThermalShockDelta(context)

        if (baselineResistance > 0L && currentResistance > 0L) {
            val resistanceDelta = Math.abs(currentResistance - baselineResistance)
            if (resistanceDelta >= maxAllowedResistanceDelta) {
                Log.e(TAG, "TAMPER BREACH: Impedance step-jump: ${resistanceDelta}uOhm (Limit: ${maxAllowedResistanceDelta}uOhm)")
                EventLogger.log(context, "FATAL: Battery terminal impedance jump (${resistanceDelta}uOhm). Intrusion detected.")
                triggerHardwareSanitize("BATTERY_SPLICING_DC_JIG_DETECTED")
                return@withContext
            }
        }

        if (currentTemp > 0L) {
            if (currentTemp < MIN_PLAUSIBLE_BATTERY_TEMP || currentTemp > MAX_PLAUSIBLE_BATTERY_TEMP) {
                Log.e(TAG, "TAMPER BREACH: Out-of-bounds thermal reading ($currentTemp).")
                EventLogger.log(context, "FATAL: Out-of-bounds battery thermal reading ($currentTemp).")
                triggerHardwareSanitize("CHASSIS_OPEN_THERMAL_ANOMALY")
                return@withContext
            }

            if (baselineTemp > 0L) {
                val tempDrop = baselineTemp - currentTemp
                if (tempDrop >= maxAllowedThermalShockDelta) {
                    Log.e(TAG, "TAMPER BREACH: Rapid thermal gradient drop: ${tempDrop / 10.0}°C (Limit: ${maxAllowedThermalShockDelta / 10.0}°C)")
                    EventLogger.log(context, "FATAL: Thermal gradient shock drop detected (${tempDrop / 10.0}°C).")
                    triggerHardwareSanitize("THERMAL_ENCLOSURE_DISASSEMBLY")
                    return@withContext
                }
            }
        }

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