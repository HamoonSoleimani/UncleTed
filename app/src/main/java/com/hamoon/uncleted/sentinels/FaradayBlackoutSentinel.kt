package com.hamoon.uncleted.sentinels

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.receivers.FaradayReceiver
import com.hamoon.uncleted.util.EventLogger
import java.util.concurrent.TimeUnit

object FaradayBlackoutSentinel {

    private const val TAG = "FaradaySentinel"
    private const val ALARM_REQ_CODE = 9911
    private const val PREFS_NAME = "faraday_blackout_de_store"
    private const val KEY_BLACKOUT_START_TIME = "blackout_start_elapsed_time"
    private const val KEY_IS_BLACKOUT_ACTIVE = "is_blackout_countdown_active"

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return

        val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }

        connectivityManager = deContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onRfRestored(deContext, "Network link re-established")
            }

            override fun onLost(network: Network) {
                evaluateTotalRfStatus(deContext)
            }

            override fun onUnavailable() {
                evaluateTotalRfStatus(deContext)
            }
        }

        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
            isInitialized = true
            Log.i(TAG, "Faraday Blackout Sentinel initialized with network callback.")
            evaluateTotalRfStatus(deContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback for Faraday sentinel", e)
        }
    }

    fun evaluateTotalRfStatus(context: Context) {
        val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }

        if (!SecurityPreferences.isFaradayBlackoutEnabled(deContext)) {
            disarmBlackoutAlarm(deContext)
            return
        }

        val hasActiveNetwork = isInternetAvailable(deContext)
        val hasCellularVoice = isCellularVoiceAvailable(deContext)

        if (!hasActiveNetwork && !hasCellularVoice) {
            onTotalRfSevered(deContext)
        } else {
            onRfRestored(deContext, "Cellular or IP link detected active")
        }
    }

    private fun onTotalRfSevered(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAlreadyCounting = prefs.getBoolean(KEY_IS_BLACKOUT_ACTIVE, false)

        if (!isAlreadyCounting) {
            val now = SystemClock.elapsedRealtime()
            prefs.edit()
                .putBoolean(KEY_IS_BLACKOUT_ACTIVE, true)
                .putLong(KEY_BLACKOUT_START_TIME, now)
                .commit()

            val durationHours = SecurityPreferences.getFaradayBlackoutDurationHours(context)
            val durationMillis = TimeUnit.HOURS.toMillis(durationHours.toLong())
            val triggerAt = now + durationMillis

            armHardwareAlarm(context, triggerAt)
            Log.w(TAG, "ALL RF CONNECTIONS SEVERED: Total Faraday blackout timer armed for $durationHours hours.")
            EventLogger.log(context, "FARADAY SENTINEL: Complete RF link loss detected. $durationHours-hour BFU countdown engaged.")
        }
    }

    private fun onRfRestored(context: Context, reason: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasActive = prefs.getBoolean(KEY_IS_BLACKOUT_ACTIVE, false)

        if (wasActive) {
            prefs.edit()
                .putBoolean(KEY_IS_BLACKOUT_ACTIVE, false)
                .remove(KEY_BLACKOUT_START_TIME)
                .commit()

            disarmBlackoutAlarm(context)
            Log.i(TAG, "RF restored ($reason). Faraday blackout countdown aborted.")
            EventLogger.log(context, "FARADAY SENTINEL: RF communications restored ($reason). Countdown aborted.")
        }
    }

    private fun armHardwareAlarm(context: Context, triggerAtElapsedRealtime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = getPendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedRealtime, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedRealtime, pendingIntent)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm permission restricted; falling back to setAndAllowWhileIdle", e)
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedRealtime, pendingIntent)
            }
        } else {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedRealtime, pendingIntent)
        }
    }

    private fun disarmBlackoutAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(getPendingIntent(context))
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, FaradayReceiver::class.java).apply {
            action = FaradayReceiver.ACTION_FARADAY_EXPIRED
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, ALARM_REQ_CODE, intent, flags)
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isCellularVoiceAvailable(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        return try {
            val serviceState = tm.serviceState
            serviceState?.state == ServiceState.STATE_IN_SERVICE
        } catch (_: SecurityException) {
            tm.simState == TelephonyManager.SIM_STATE_READY
        }
    }
}