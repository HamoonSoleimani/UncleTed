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
        private const val RSRP_DEAD_ZONE_THRESHOLD = -135 // dBm
    }

    fun evaluateSpectralCollapse() {
        if (!SecurityPreferences.isSpectralSentinelEnabled(context)) {
            zeroSignalStartEpoch = 0L
            return
        }

        // Rule 1: Do not trigger if user intentionally toggled Airplane Mode
        val isAirplaneMode = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0
        if (isAirplaneMode) {
            zeroSignalStartEpoch = 0L
            return
        }

        // Rule 2: Only enforce when screen is locked
        val isLocked = keyguardManager?.isDeviceLocked ?: true
        if (!isLocked) {
            zeroSignalStartEpoch = 0L
            return
        }

        val isCellularDead = evaluateCellularDeadState()
        val isWifiDead = evaluateWifiDeadState()

        val motionRequired = SecurityPreferences.isSpectralMotionRequired(context)
        val isMotionConditionMet = if (motionRequired) MotionDetector.hasMicroMotion() else true

        if (isCellularDead && isWifiDead && isMotionConditionMet) {
            val now = SystemClock.elapsedRealtime()
            val quarantineWindowMs = SecurityPreferences.getSpectralQuarantineMs(context)

            if (zeroSignalStartEpoch == 0L) {
                zeroSignalStartEpoch = now
                Log.w(TAG, "Spectral anomaly: Multi-spectrum RF collapse detected. Quarantine timer initiated ($quarantineWindowMs ms)...")
            } else if (now - zeroSignalStartEpoch >= quarantineWindowMs) {
                zeroSignalStartEpoch = 0L
                Log.e(TAG, "!!! CONFIRMED FARADAY BAG ISOLATION SEIZURE DETECTED (SUSTAINED ${quarantineWindowMs}ms) !!!")
                EventLogger.log(context, "CRITICAL: Faraday bag seizure confirmed. Executing AFU -> BFU key eviction.")
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