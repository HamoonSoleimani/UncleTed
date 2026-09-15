package com.hamoon.uncleted.sentinels

import android.content.Context
import android.os.Build
import android.telephony.NetworkRegistrationInfo
import android.telephony.PhoneStateListener
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

class BasebandDowngradeSentinel(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private var telephonyCallback: Any? = null
    private var legacyListener: PhoneStateListener? = null
    private var isMonitoring = false

    companion object {
        private const val TAG = "BasebandSentinel"
        private const val COOLDOWN_WINDOW_MS = 10_000L
        @Volatile
        private var lastTriggerTimestamp = 0L
    }

    fun start() {
        if (isMonitoring || telephonyManager == null) return
        if (!PermissionUtils.hasReadPhoneStatePermission(context)) {
            Log.w(TAG, "READ_PHONE_STATE permission absent; baseband downgrade monitoring aborted.")
            return
        }

        if (!SecurityPreferences.isBasebandSentinelEnabled(context)) {
            Log.d(TAG, "Baseband Downgrade Sentinel is disabled in preferences.")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                    override fun onServiceStateChanged(serviceState: ServiceState) {
                        evaluateServiceState(serviceState)
                    }
                }
                telephonyManager.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onServiceStateChanged(serviceState: ServiceState?) {
                        serviceState?.let { evaluateServiceState(it) }
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE)
                legacyListener = listener
            }
            isMonitoring = true
            Log.i(TAG, "Baseband Downgrade Sentinel initialized and actively monitoring link-layer.")
            EventLogger.log(context, "SENTINEL: Baseband 2G downgrade and Stingray monitor armed.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register baseband telephony callback", e)
        }
    }

    fun stop() {
        if (!isMonitoring || telephonyManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let {
                    telephonyManager.unregisterTelephonyCallback(it)
                }
                telephonyCallback = null
            } else {
                legacyListener?.let {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                }
                legacyListener = null
            }
            isMonitoring = false
            Log.i(TAG, "Baseband Downgrade Sentinel disarmed.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister baseband telephony callback", e)
        }
    }

    private fun evaluateServiceState(serviceState: ServiceState) {
        if (!SecurityPreferences.isBasebandSentinelEnabled(context)) return

        val regInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceState.networkRegistrationInfoList
        } else {
            emptyList()
        }

        var isDowngradeDetected = false
        var detectedTech = TelephonyManager.NETWORK_TYPE_UNKNOWN

        if (regInfoList.isNotEmpty()) {
            for (info in regInfoList) {
                if (info.domain == NetworkRegistrationInfo.DOMAIN_CS || info.domain == NetworkRegistrationInfo.DOMAIN_PS) {
                    val tech = info.accessNetworkTechnology
                    if (isLegacy2GTechnology(tech)) {
                        isDowngradeDetected = true
                        detectedTech = tech
                        break
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val networkType = telephonyManager?.networkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
            if (isLegacy2GTechnology(networkType)) {
                isDowngradeDetected = true
                detectedTech = networkType
            }
        }

        if (isDowngradeDetected) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerTimestamp < COOLDOWN_WINDOW_MS) return
            lastTriggerTimestamp = now

            Log.e(TAG, "CRITICAL: Forced baseband downgrade to 2G detected (Tech: $detectedTech)! Potential IMSI-Catcher / Stingray.")
            EventLogger.log(context, "CRITICAL: Forced 2G downgrade detected (Tech Code: $detectedTech). Engaging radio killswitch.")

            triggerRadioCountermeasures()
        }
    }

    private fun isLegacy2GTechnology(tech: Int): Boolean {
        return tech == TelephonyManager.NETWORK_TYPE_GSM ||
                tech == TelephonyManager.NETWORK_TYPE_GPRS ||
                tech == TelephonyManager.NETWORK_TYPE_EDGE ||
                tech == TelephonyManager.NETWORK_TYPE_CDMA ||
                tech == TelephonyManager.NETWORK_TYPE_1xRTT ||
                tech == TelephonyManager.NETWORK_TYPE_IDEN
    }

    private fun triggerRadioCountermeasures() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val strategy = DefenseCoordinator.resolveStrategy(context)
                strategy.isolateRadiosAndNetwork()
                PanicActionService.trigger(
                    context,
                    "STINGRAY_2G_DOWNGRADE_DETECTED",
                    PanicActionService.Severity.HIGH
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed executing radio cutoff countermeasures", e)
            }
        }
    }
}