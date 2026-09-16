package com.hamoon.uncleted.sentinels

import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.NetworkRegistrationInfo
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdvancedBasebandSentinel(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private var registeredCallback: Any? = null
    private var isMonitoring = false

    companion object {
        private const val TAG = "AdvancedBasebandSentinel"
        private const val ALERT_COOLDOWN_MS = 15_000L
        @Volatile
        private var lastAlertTimestamp = 0L

        private const val NETWORK_TYPE_BITMASK_GSM_LOCAL = 1L shl (TelephonyManager.NETWORK_TYPE_GSM - 1)
        private const val NETWORK_TYPE_BITMASK_GPRS_LOCAL = 1L shl (TelephonyManager.NETWORK_TYPE_GPRS - 1)
        private const val NETWORK_TYPE_BITMASK_EDGE_LOCAL = 1L shl (TelephonyManager.NETWORK_TYPE_EDGE - 1)
        private const val NETWORK_TYPE_BITMASK_CDMA_LOCAL = 1L shl (TelephonyManager.NETWORK_TYPE_CDMA - 1)
        private const val NETWORK_TYPE_BITMASK_1XRTT_LOCAL = 1L shl (TelephonyManager.NETWORK_TYPE_1xRTT - 1)

        private const val ALL_2G_BITMASK = NETWORK_TYPE_BITMASK_GSM_LOCAL or
                NETWORK_TYPE_BITMASK_GPRS_LOCAL or
                NETWORK_TYPE_BITMASK_EDGE_LOCAL or
                NETWORK_TYPE_BITMASK_CDMA_LOCAL or
                NETWORK_TYPE_BITMASK_1XRTT_LOCAL
    }

    fun start() {
        if (isMonitoring || telephonyManager == null) return
        if (!PermissionUtils.hasReadPhoneStatePermission(context)) {
            Log.w(TAG, "READ_PHONE_STATE permission missing; baseband sentinel aborted.")
            return
        }

        if (SecurityPreferences.isHardware2GDisabled(context)) {
            enforceModemLevel2GBlock()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val callback = object : TelephonyCallback(),
                    TelephonyCallback.ServiceStateListener,
                    TelephonyCallback.CellInfoListener {

                    override fun onServiceStateChanged(serviceState: ServiceState) {
                        if (SecurityPreferences.isBasebandSentinelEnabled(context)) {
                            evaluateServiceStateDowngrade(serviceState)
                        }
                    }

                    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                        if (SecurityPreferences.isBasebandSentinelEnabled(context)) {
                            evaluateRogueBaseStationSignatures(cellInfo)
                        }
                    }
                }

                telephonyManager.registerTelephonyCallback(
                    ContextCompat.getMainExecutor(context),
                    callback
                )
                registeredCallback = callback
                isMonitoring = true
                Log.i(TAG, "Advanced Baseband Sentinel armed.")
                EventLogger.log(context, "BASEBAND: Advanced IMSI-Catcher & cell anomaly sentinel armed.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed registering advanced telephony callbacks", e)
            }
        }
    }

    fun stop() {
        if (!isMonitoring || telephonyManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (registeredCallback as? TelephonyCallback)?.let {
                    telephonyManager.unregisterTelephonyCallback(it)
                }
                registeredCallback = null
            }
            isMonitoring = false
            Log.i(TAG, "Advanced Baseband Sentinel disarmed.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed unregistering telephony callbacks", e)
        }
    }

    fun enforceModemLevel2GBlock() {
        if (telephonyManager == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val currentMask = telephonyManager.getAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
                )
                val sanitizedMask = currentMask and ALL_2G_BITMASK.inv()

                if (currentMask != sanitizedMask) {
                    telephonyManager.setAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                        sanitizedMask
                    )
                    Log.i(TAG, "Baseband modem allowed network types bitmask updated (2G stripped: $sanitizedMask).")
                    EventLogger.log(context, "BASEBAND: Modem-level 2G frequency scanning permanently masked out.")
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "setAllowedNetworkTypesForReason requires privileged carrier/system authority: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed updating allowed network types bitmask", e)
            }
        }
    }

    fun restoreModemNetworkTypes() {
        if (telephonyManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val currentMask = telephonyManager.getAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
                )
                val restoredMask = currentMask or ALL_2G_BITMASK
                telephonyManager.setAllowedNetworkTypesForReason(
                    TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                    restoredMask
                )
                Log.i(TAG, "Baseband modem network types restored.")
                EventLogger.log(context, "BASEBAND: Baseband modem 2G bitmask restored.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed restoring network types bitmask", e)
            }
        }
    }

    private fun evaluateServiceStateDowngrade(serviceState: ServiceState) {
        val regInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceState.networkRegistrationInfoList
        } else {
            emptyList()
        }

        for (info in regInfoList) {
            if (info.domain == NetworkRegistrationInfo.DOMAIN_CS || info.domain == NetworkRegistrationInfo.DOMAIN_PS) {
                val tech = info.accessNetworkTechnology
                if (is2GTechnology(tech)) {
                    triggerBasebandThreatAlert(
                        "FORCED_2G_LINK_DOWNGRADE",
                        "Forced legacy 2G cellular link negotiated (Technology Code: $tech)."
                    )
                    return
                }
            }
        }
    }

    private fun evaluateRogueBaseStationSignatures(cellInfoList: List<CellInfo>) {
        if (cellInfoList.isEmpty()) return

        val maxAllowedTimingAdvance = SecurityPreferences.getTimingAdvanceThreshold(context)

        for (info in cellInfoList) {
            if (info is CellInfoGsm) {
                if (SecurityPreferences.isHardware2GDisabled(context)) {
                    triggerBasebandThreatAlert(
                        "ROGUE_2G_BASE_STATION_DETECTED",
                        "Device connected to rogue GSM cell despite hardware 2G masking."
                    )
                    return
                }
            }

            if (info is CellInfoLte) {
                val signalStrength = info.cellSignalStrength
                val rsrp = signalStrength.rsrp
                val timingAdvance = signalStrength.timingAdvance

                if (rsrp > -65 && timingAdvance > maxAllowedTimingAdvance && timingAdvance != Int.MAX_VALUE) {
                    val estimatedMeters = timingAdvance * 78
                    triggerBasebandThreatAlert(
                        "STINGRAY_TIMING_ADVANCE_SPOOF",
                        "Impossible RF topology: High power (${rsrp}dBm) with Timing Advance ${timingAdvance} (~${estimatedMeters}m)."
                    )
                    return
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun is2GTechnology(tech: Int): Boolean {
        return tech == TelephonyManager.NETWORK_TYPE_GSM ||
                tech == TelephonyManager.NETWORK_TYPE_GPRS ||
                tech == TelephonyManager.NETWORK_TYPE_EDGE ||
                tech == TelephonyManager.NETWORK_TYPE_CDMA ||
                tech == TelephonyManager.NETWORK_TYPE_1xRTT ||
                tech == TelephonyManager.NETWORK_TYPE_IDEN
    }

    private fun triggerBasebandThreatAlert(reason: String, description: String) {
        val now = System.currentTimeMillis()
        if (now - lastAlertTimestamp < ALERT_COOLDOWN_MS) return
        lastAlertTimestamp = now

        Log.e(TAG, "!!! BASEBAND SECURITY BREACH: $reason !!!")
        Log.e(TAG, "Details: $description")
        EventLogger.log(context, "CRITICAL: Baseband threat [$reason]: $description")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val strategy = DefenseCoordinator.resolveStrategy(context)
                strategy.isolateRadiosAndNetwork()

                PanicActionService.trigger(
                    context,
                    reason,
                    PanicActionService.Severity.HIGH
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed executing baseband cutoff countermeasures", e)
            }
        }
    }
}