package com.hamoon.uncleted.services

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.NotificationHelper
import com.hamoon.uncleted.util.RootActions
import com.hamoon.uncleted.util.UsbDetector
import kotlinx.coroutines.*

class UsbTripwireService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isMonitoring = false
    private lateinit var keyguardManager: KeyguardManager

    companion object {
        private const val TAG = "UsbTripwireService"
        private const val POLLING_INTERVAL_MS = 1000L // Check every 1 second
    }

    override fun onCreate() {
        super.onCreate()
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        startForeground(2002, NotificationHelper.createBasicNotification(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SecurityPreferences.isFirewallTripwireEnabled(this)) { // Reuse firewall preference or create new one
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isMonitoring) {
            isMonitoring = true
            startKernelMonitoring()
        }
        return START_STICKY
    }

    private fun startKernelMonitoring() {
        serviceScope.launch {
            Log.i(TAG, "USB Tripwire Armed. Monitoring Kernel SysFS...")

            while (isActive) {
                // 1. Only ACT if the device is LOCKED.
                // If unlocked, we assume the owner is using it.
                if (keyguardManager.isDeviceLocked) {

                    // 2. Check Kernel for Data Connection
                    val isDataConnected = UsbDetector.isDataCableConnected(this@UsbTripwireService)

                    if (isDataConnected) {
                        Log.e(TAG, "!!! USB DATA CONNECTION DETECTED WHILE LOCKED !!!")
                        Log.e(TAG, "!!! EXECUTING SYSTEM KILL SWITCH !!!")

                        // 3. EXECUTE KILL
                        // We do not send alerts. We do not take photos. We destroy.
                        // Speed is the only variable that matters here.

                        // A. Block inputs to prevent cancellation
                        RootActions.blockAllNetworkTraffic(this@UsbTripwireService) // Optional: Kill comms

                        // B. The Nuclear Option
                        try {
                            // Direct call to wipe mechanism
                            RootActions.performSecureWipePlus(this@UsbTripwireService)
                        } catch (e: Exception) {
                            // Last resort fallback
                            PanicActionService.trigger(this@UsbTripwireService, "USB_TRIPWIRE_FAIL", PanicActionService.Severity.CRITICAL)
                        }

                        // Stop loop
                        stopSelf()
                    }
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}