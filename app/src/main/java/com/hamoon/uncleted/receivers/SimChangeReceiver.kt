package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService

class SimChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.SIM_STATE_CHANGED") {
            if (!SecurityPreferences.isSimChangeAlertEnabled(context)) {
                Log.d("SimChangeReceiver", "SIM change alert disabled in settings.")
                return
            }

            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val currentSimState = telephonyManager.simState

            if (currentSimState == TelephonyManager.SIM_STATE_READY) {
                try {
                    val currentIccSerialNumber = telephonyManager.simSerialNumber
                    val storedIccSerialNumber = SecurityPreferences.getInitialSimSerial(context)

                    if (storedIccSerialNumber == null) {
                        SecurityPreferences.setInitialSimSerial(context, currentIccSerialNumber)
                        Log.d("SimChangeReceiver", "Initial SIM ICC serial set: $currentIccSerialNumber")
                    } else if (storedIccSerialNumber != currentIccSerialNumber) {
                        Log.w("SimChangeReceiver", "SIM card changed! Old: $storedIccSerialNumber, New: $currentIccSerialNumber")
                        PanicActionService.trigger(context, "SIM_CHANGED", PanicActionService.Severity.MEDIUM)
                        SecurityPreferences.setInitialSimSerial(context, currentIccSerialNumber)
                    }
                } catch (e: SecurityException) {
                    Log.e("SimChangeReceiver", "Permission denied for reading SIM serial number.", e)
                }
            } else if (currentSimState == TelephonyManager.SIM_STATE_ABSENT) {
                val storedIccSerialNumber = SecurityPreferences.getInitialSimSerial(context)
                val isBootGracePeriod = SystemClock.elapsedRealtime() < 60_000L

                if (!storedIccSerialNumber.isNullOrEmpty() && !isBootGracePeriod) {
                    Log.w("SimChangeReceiver", "SIM card removed after setup! Triggering alert.")
                    PanicActionService.trigger(context, "SIM_REMOVED", PanicActionService.Severity.MEDIUM)
                } else {
                    Log.d("SimChangeReceiver", "SIM_STATE_ABSENT ignored (initial setup or boot grace period).")
                }
                // Stored serial is preserved so a replacement SIM will still trigger SIM_CHANGED
            }
        }
    }
}