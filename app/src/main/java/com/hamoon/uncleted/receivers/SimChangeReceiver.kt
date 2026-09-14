package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.PermissionUtils
import com.hamoon.uncleted.util.RootChecker
import com.hamoon.uncleted.util.RootExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SimChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SimChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.SIM_STATE_CHANGED") return

        if (!SecurityPreferences.isSimChangeAlertEnabled(context)) {
            Log.d(TAG, "SIM change alert disabled in settings.")
            return
        }

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        val currentSimState = telephonyManager.simState

        when (currentSimState) {
            TelephonyManager.SIM_STATE_READY -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        evaluateSimState(context, telephonyManager)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            TelephonyManager.SIM_STATE_ABSENT -> {
                val storedIdentifier = SecurityPreferences.getInitialSimSerial(context)
                val isBootGracePeriod = SystemClock.elapsedRealtime() < 60_000L

                if (!storedIdentifier.isNullOrEmpty() && !isBootGracePeriod) {
                    Log.w(TAG, "SIM card removed after initial setup! Triggering alert.")
                    PanicActionService.trigger(context, "SIM_REMOVED", PanicActionService.Severity.MEDIUM)
                }
            }
        }
    }

    private suspend fun evaluateSimState(context: Context, telephonyManager: TelephonyManager) {
        val currentIdentifier = retrieveSimIdentifier(context, telephonyManager)

        if (currentIdentifier.isNullOrEmpty()) {
            Log.w(TAG, "Could not determine a reliable SIM hardware identifier on this Android build.")
            return
        }

        val storedIdentifier = SecurityPreferences.getInitialSimSerial(context)

        if (storedIdentifier == null) {
            SecurityPreferences.setInitialSimSerial(context, currentIdentifier)
            Log.i(TAG, "Baseline SIM identifier saved: $currentIdentifier")
        } else if (storedIdentifier != currentIdentifier) {
            Log.w(TAG, "SIM card mismatch detected! Baseline: $storedIdentifier, Current: $currentIdentifier")
            PanicActionService.trigger(context, "SIM_CHANGED", PanicActionService.Severity.MEDIUM)
            SecurityPreferences.setInitialSimSerial(context, currentIdentifier)
        }
    }

    /**
     * Safely queries the SIM identifier across Android 9 through 14+ without throwing SecurityExceptions.
     */
    private suspend fun retrieveSimIdentifier(context: Context, telephonyManager: TelephonyManager): String? {
        // Method 1: Privileged direct API call (valid on pre-Android 10 or if system-granted)
        try {
            if (PermissionUtils.hasReadPhoneStatePermission(context)) {
                @Suppress("DEPRECATION")
                val serial = telephonyManager.simSerialNumber
                if (!serial.isNullOrEmpty()) return serial
            }
        } catch (_: SecurityException) {
            // Expected on Android 10+ (API 29+) without READ_PRIVILEGED_PHONE_STATE
        }

        // Method 2: SubscriptionManager subscription identification
        try {
            if (PermissionUtils.hasReadPhoneStatePermission(context)) {
                val subManager = context.getSystemService(SubscriptionManager::class.java)
                val activeSubs = subManager?.activeSubscriptionInfoList
                if (!activeSubs.isNullOrEmpty()) {
                    val subInfo = activeSubs.first()
                    if (!subInfo.iccId.isNullOrEmpty()) {
                        return subInfo.iccId
                    }
                    val simId = "${subInfo.subscriptionId}_${subInfo.mccString}_${subInfo.mncString}"
                    if (simId.isNotEmpty()) return simId
                }
            }
        } catch (_: SecurityException) {}

        // Method 3: Elevated Root Shell fallback via telephony properties / RIL
        if (RootChecker.isDeviceRooted()) {
            val rootIccid = RootExecutor.run("getprop ril.iccid.sim1").output.firstOrNull()?.trim()
            if (!rootIccid.isNullOrEmpty() && rootIccid != "null") return rootIccid

            val rootOperator = RootExecutor.run("getprop gsm.sim.operator.numeric").output.firstOrNull()?.trim()
            if (!rootOperator.isNullOrEmpty() && rootOperator != "null") return rootOperator
        }

        // Method 4: Safe Operator/ISO fingerprint
        val fallbackFingerprint = "${telephonyManager.simOperator}_${telephonyManager.simCountryIso}"
        return if (fallbackFingerprint.length > 2 && !fallbackFingerprint.startsWith("_")) {
            fallbackFingerprint
        } else {
            null
        }
    }
}