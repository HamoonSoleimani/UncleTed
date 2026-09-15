package com.hamoon.uncleted.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.receivers.TripwireReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object TripwireManager {

    private const val TAG = "TripwireManager"
    private const val ALARM_REQUEST_CODE = 8801

    fun scheduleOrCancelTripwire(context: Context) {
        if (SecurityPreferences.isTripwireEnabled(context)) {
            SecurityPreferences.setLastTripwireCheckIn(context, System.currentTimeMillis())
            armTripwire(context)
        } else {
            cancelTripwire(context)
            SecurityPreferences.setLastTripwireCheckIn(context, 0L)
        }
    }

    fun armTripwire(context: Context) {
        if (!SecurityPreferences.isTripwireEnabled(context)) {
            Log.d(TAG, "Tripwire is disabled, skipping arming.")
            return
        }

        val durationHours = SecurityPreferences.getTripwireDuration(context).toLong()
        val triggerAtEpoch = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(durationHours)

        setHardwareAlarm(context, triggerAtEpoch)
        Log.i(TAG, "Hardware Tripwire armed via AlarmManager for $durationHours hours (Epoch: $triggerAtEpoch)")
        EventLogger.log(context, "TRIPWIRE: Armed for $durationHours hours.")
    }

    fun cancelTripwire(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = getAlarmPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        Log.i(TAG, "Hardware Tripwire alarm canceled.")
        EventLogger.log(context, "TRIPWIRE: Disarmed.")
    }

    fun checkIn(context: Context) {
        if (!SecurityPreferences.isTripwireEnabled(context)) {
            return
        }
        Log.i(TAG, "Device activity/network check-in verified. Resetting tripwire hardware alarm.")
        SecurityPreferences.setLastTripwireCheckIn(context, System.currentTimeMillis())
        cancelTripwire(context)
        armTripwire(context)
    }

    /**
     * Evaluates tripwire state during Direct Boot (BFU) immediately upon LOCKED_BOOT_COMPLETED.
     * Computes elapsed time using Device-Protected storage timestamps.
     * If the phone was isolated in a Faraday bag or powered down past the deadline, it triggers immediate erasure.
     */
    fun scheduleFromLastCheckIn(context: Context) {
        if (!SecurityPreferences.isTripwireEnabled(context)) {
            Log.d(TAG, "Tripwire disabled in BFU preferences. Skipping BFU evaluation.")
            return
        }

        val lastCheckIn = SecurityPreferences.getLastTripwireCheckIn(context)
        if (lastCheckIn == 0L) {
            armTripwire(context)
            return
        }

        val durationMillis = TimeUnit.HOURS.toMillis(SecurityPreferences.getTripwireDuration(context).toLong())
        val deadlineEpoch = lastCheckIn + durationMillis
        val remainingMillis = deadlineEpoch - System.currentTimeMillis()

        if (remainingMillis <= 0L) {
            Log.e(TAG, "CRITICAL: Tripwire deadline expired while offline/powered down! Executing BFU wipe.")
            EventLogger.log(context, "CRITICAL: Dead-man tripwire expired during downtime. Initiating wipe.")
            CoroutineScope(Dispatchers.IO).launch {
                val strategy = DefenseCoordinator.resolveStrategy(context)
                strategy.executeWipe("BFU_TRIPWIRE_EXPIRED_DURING_DOWNTIME")
            }
            return
        }

        Log.i(TAG, "Re-arming tripwire in BFU state. Remaining: ${remainingMillis / 1000 / 60} minutes.")
        setHardwareAlarm(context, deadlineEpoch)
    }

    private fun setHardwareAlarm(context: Context, triggerAtEpoch: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = getAlarmPendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpoch, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpoch, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpoch, pendingIntent)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm permission missing, scheduling via setAndAllowWhileIdle", e)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpoch, pendingIntent)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtEpoch, pendingIntent)
        }
    }

    private fun getAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TripwireReceiver::class.java).apply {
            action = TripwireReceiver.ACTION_TRIPWIRE_EXPIRED
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
    }
}