package com.hamoon.uncleted.services

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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

    private var consecutiveDataHits = 0

    companion object {
        private const val TAG = "UsbTripwireService"
        private const val NOTIFICATION_ID = 2002
        private const val POLLING_INTERVAL_MS = 1000L

        // Requires 2 consecutive positive evaluations (2 seconds apart)
        // to avoid triggering on momentary voltage renegotiations when plugging into smart chargers.
        private const val REQUIRED_CONSECUTIVE_HITS = 2
    }

    override fun onCreate() {
        super.onCreate()
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        val notification = NotificationHelper.createBasicNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start UsbTripwireService in foreground", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SecurityPreferences.isUsbTripwireEnabled(this)) {
            Log.w(TAG, "UsbTripwireService started but feature is disabled. Stopping service.")
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
            Log.i(TAG, "USB Tripwire Armed. Monitoring Kernel UDC state...")

            while (isActive) {
                if (keyguardManager.isDeviceLocked) {
                    val isDataConnected = UsbDetector.isDataCableConnected(this@UsbTripwireService)

                    if (isDataConnected) {
                        consecutiveDataHits++
                        Log.w(TAG, "USB data connection detected while locked ($consecutiveDataHits/$REQUIRED_CONSECUTIVE_HITS)")

                        if (consecutiveDataHits >= REQUIRED_CONSECUTIVE_HITS) {
                            Log.e(TAG, "!!! CONFIRMED USB DATA CONNECTION TO HOST WHILE LOCKED !!!")
                            Log.e(TAG, "!!! EXECUTING SYSTEM KILL SWITCH !!!")

                            RootActions.blockAllNetworkTraffic(this@UsbTripwireService)

                            try {
                                RootActions.performSecureWipePlus(this@UsbTripwireService)
                            } catch (e: Exception) {
                                Log.e(TAG, "Direct secure wipe failed, triggering panic fallback", e)
                                PanicActionService.trigger(
                                    this@UsbTripwireService,
                                    "USB_TRIPWIRE_FAIL",
                                    PanicActionService.Severity.CRITICAL
                                )
                            }
                            stopSelf()
                            break
                        }
                    } else {
                        consecutiveDataHits = 0
                    }
                } else {
                    consecutiveDataHits = 0
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