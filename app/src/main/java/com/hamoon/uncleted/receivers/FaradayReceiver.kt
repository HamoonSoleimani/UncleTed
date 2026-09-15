package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FaradayReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "FaradayReceiver"
        const val ACTION_FARADAY_EXPIRED = "com.hamoon.uncleted.ACTION_FARADAY_EXPIRED"
        private const val WAKELOCK_TAG = "uncleted:faraday_blackout_wakelock"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FARADAY_EXPIRED) return

        val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }

        if (!SecurityPreferences.isFaradayBlackoutEnabled(deContext)) {
            Log.d(TAG, "Faraday alarm received, but feature is disabled in preferences.")
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
        wakeLock.acquire(60_000L)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.e(TAG, "!!! FARADAY BLACKOUT INTERVAL EXPIRED (180 MIN CONTINUOUS RF ISOLATION) !!!")
                EventLogger.log(context, "CRITICAL: Faraday blackout duration exceeded. Hardware seizure presumed. Evicting to BFU.")

                val strategy = DefenseCoordinator.resolveStrategy(context)

                // Strategy handles cold BFU enforcement:
                // DeviceOwnerStrategy issues an instant dpm.reboot(), dropping all volatile keys to cold BFU.
                // RootPrivilegedStrategy invokes vdc cryptfs lockuser 0 and drops the kernel keyring.
                strategy.evictMemoryKeysAndLock()

                // Dispatch secondary panic alert service trigger
                PanicActionService.trigger(
                    context,
                    "FARADAY_BLACKOUT_EXPIRED",
                    PanicActionService.Severity.CRITICAL
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failure during Faraday blackout execution protocol", e)
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
                pendingResult.finish()
            }
        }
    }
}