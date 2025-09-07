package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hamoon.uncleted.MainActivity
import com.hamoon.uncleted.data.SecurityPreferences

/**
 * The correct, modern way to handle secret dialer codes.
 * This receiver listens for the system broadcast `android.provider.Telephony.SECRET_CODE`.
 * It is triggered when the user types a code in the format *#*#CODE#*#*
 */
class SecretCodeReceiver : BroadcastReceiver() {

    private val TAG = "SecretCodeReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SECRET_CODE") {
            val secretCode = SecurityPreferences.getSecretDialerCode(context)
            val uri = intent.data
            val host = uri?.host

            Log.d(TAG, "Secret code broadcast received. Host: $host")

            if (!secretCode.isNullOrEmpty() && host == secretCode) {
                Log.i(TAG, "Secret code matched! Launching MainActivity.")
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(launchIntent)
            } else {
                Log.w(TAG, "Received code '$host' does not match stored code '$secretCode'.")
            }
        }
    }
}