package com.hamoon.uncleted.sentinels

import android.app.KeyguardManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.MotionDetector
import com.hamoon.uncleted.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpectralSentinel(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    private var zeroSignalStartEpoch: Long = 0L

    companion object {
        private const val TAG = "SpectralSentinel"
        private const val QUARANTINE_THRESHOLD_MS = 4000L // 4-second sustained confirmation
        private const val RSRP_DEAD_ZONE_THRESHOLD = -135 // dBm
    }

    fun evaluateSpectralCollapse() {
        if (!SecurityPreferences.isSpectralSentinelEnabled(context)) {
            zeroSignalStartEpoch = 0L
            return
        }

        // Rule 1: Ignore state if user deliberately toggled Airplane Mode
        val isAirplaneMode = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0
        if (isAirplaneMode) {
            zeroSignalStartEpoch = 0L
            return
        }

        // Rule 2: Only active when screen is locked
        val isLocked = keyguardManager?.isDeviceLocked ?: true
        if (!isLocked) {
            zeroSignalStartEpoch = 0L
            return
        }

        // 1. Telephony: Assess serving cell signals across physical modems
        val isCellularDead = evaluateCellularDeadState()

        // 2. Wi-Fi: Active scan returns 0 visible BSSIDs
        val isWifiDead = evaluateWifiDeadState()

        // 3. Movement Sensor: Verify device is handled or in physical transit
        val isDeviceInMotion = MotionDetector.hasMicroMotion()

        if (isCellularDead && isWifiDead && isDeviceInMotion) {
            val now = SystemClock.elapsedRealtime()
            if (zeroSignalStartEpoch == 0L) {
                zeroSignalStartEpoch = now
                Log.w(TAG, "Spectral anomaly: Sudden multi-spectrum RF drop with physical motion detected. Verification clock started.")
            } else if (now - zeroSignalStartEpoch >= QUARANTINE_THRESHOLD_MS) {
                zeroSignalStartEpoch = 0L
                Log.e(TAG, "!!! CONFIRMED FARADAY BAG ISOLATION SEIZURE DETECTED (SUSTAINED 4S) !!!")
                EventLogger.log(context, "CRITICAL: Faraday bag physical seizure confirmed. Executing instant AFU -> BFU key eviction.")
                executeInstantBfuEviction()
            }
        } else {
            zeroSignalStartEpoch = 0L
        }
    }

    private fun evaluateCellularDeadState(): Boolean {
        if (!PermissionUtils.hasLocationPermissions(context) || !PermissionUtils.hasReadPhoneStatePermission(context)) {
            return telephonyManager?.simState == TelephonyManager.SIM_STATE_ABSENT
        }

        return try {
            val cellList: List<CellInfo>? = telephonyManager?.allCellInfo
            if (cellList.isNullOrEmpty()) {
                true
            } else {
                cellList.all { info ->
                    when (info) {
                        is CellInfoLte -> info.cellSignalStrength.rsrp < RSRP_DEAD_ZONE_THRESHOLD
                        is CellInfoNr -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val nrStrength = info.cellSignalStrength as? CellSignalStrengthNr
                                (nrStrength?.ssRsrp ?: -140) < RSRP_DEAD_ZONE_THRESHOLD
                            } else {
                                true
                            }
                        }
                        is CellInfoWcdma -> info.cellSignalStrength.dbm < RSRP_DEAD_ZONE_THRESHOLD
                        is CellInfoGsm -> info.cellSignalStrength.dbm < RSRP_DEAD_ZONE_THRESHOLD
                        else -> true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying cell info: ${e.message}")
            false
        }
    }

    private fun evaluateWifiDeadState(): Boolean {
        return try {
            val scanResults = wifiManager?.scanResults
            scanResults.isNullOrEmpty()
        } catch (_: Exception) {
            true
        }
    }

    private fun executeInstantBfuEviction() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val strategy = DefenseCoordinator.resolveStrategy(context)
                strategy.evictMemoryKeysAndLock()
                PanicActionService.trigger(
                    context,
                    "FARADAY_BAG_SEIZURE_TRIGGERED",
                    PanicActionService.Severity.CRITICAL
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error during Faraday BFU eviction", e)
            }
        }
    }
}