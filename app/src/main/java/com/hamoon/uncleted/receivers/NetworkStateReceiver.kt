package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hamoon.uncleted.sentinels.FaradayBlackoutSentinel
import com.hamoon.uncleted.util.NetworkUtils
import com.hamoon.uncleted.util.TripwireManager

class NetworkStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        @Suppress("DEPRECATION")
        if (intent?.action == android.net.ConnectivityManager.CONNECTIVITY_ACTION ||
            intent?.action == "android.net.conn.CONNECTIVITY_CHANGE") {

            if (NetworkUtils.isNetworkAvailable(context)) {
                // If network connection is active, check in with standard offline tripwire
                TripwireManager.checkIn(context)
            }

            // Continuously evaluate all links to maintain the Faraday Blackout countdown
            FaradayBlackoutSentinel.evaluateTotalRfStatus(context)
        }
    }
}