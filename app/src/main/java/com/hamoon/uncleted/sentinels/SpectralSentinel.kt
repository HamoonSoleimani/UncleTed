package com.hamoon.uncleted.sentinels

import android.app.KeyguardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

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

        // Rule 1: Do not trigger if user intentionally enabled Airplane Mode
        val isAirplaneMode = try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0
        } catch (_: Exception) {
            false
        }
        if (isAirplaneMode) {
            zeroSignalStartEpoch = 0L
            return
        }

        // Rule 2: Only evaluate when the screen is locked
        val isLocked = try {
            keyguardManager?.isDeviceLocked ?: false
        } catch (_: Exception) {
            false
        }
        if (!isLocked) {
            zeroSignalStartEpoch = 0L
            return
        }

        // Rule 3: If device has an active Wi-Fi or cellular IP connection, it is NOT in a Faraday bag
        if (hasActiveInternetConnection()) {
            zeroSignalStartEpoch = 0L
            return
        }

        val isCellularDead = evaluateCellularDeadState()
        val motionRequired = SecurityPreferences.isSpectralMotionRequired(context)
        val isMotionConditionMet = if (motionRequired) MotionDetector.hasMicroMotion() else true

        if (isCellularDead && isMotionConditionMet) {
            val now = SystemClock.elapsedRealtime()
            val quarantineWindowMs = maxOf(SecurityPreferences.getSpectralQuarantineMs(context), 15000L)

            if (zeroSignalStartEpoch == 0L) {
                zeroSignalStartEpoch = now
                Log.w(TAG, "Spectral anomaly: Total RF link loss detected. Quarantine timer started (${quarantineWindowMs}ms)...")
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

    private fun hasActiveInternetConnection(): Boolean {
        return try {
            val activeNetwork = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun evaluateCellularDeadState(): Boolean {
        if (!PermissionUtils.hasLocationPermissions(context) || !PermissionUtils.hasReadPhoneStatePermission(context)) {
            return false
        }

        return try {
            val cellList: List<CellInfo>? = telephonyManager?.allCellInfo
            if (cellList.isNullOrEmpty()) {
                telephonyManager?.simState == TelephonyManager.SIM_STATE_ABSENT
            } else {
                cellList.filter { it.isRegistered }.all { info ->
                    when (info) {
                        is CellInfoLte -> info.cellSignalStrength.rsrp < RSRP_DEAD_ZONE_THRESHOLD
                        is CellInfoNr -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val nrStrength = info.cellSignalStrength as? CellSignalStrengthNr
                                (nrStrength?.ssRsrp ?: -140) < RSRP_DEAD_ZONE_THRESHOLD
                            } else true
                        }
                        is CellInfoWcdma -> info.cellSignalStrength.dbm < RSRP_DEAD_ZONE_THRESHOLD
                        is CellInfoGsm -> info.cellSignalStrength.dbm < RSRP_DEAD_ZONE_THRESHOLD
                        else -> false
                    }
                }
            }
        } catch (e: Exception) {
            false
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
